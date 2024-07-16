// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow.argo

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.WorkflowStatusRequest
import com.cosmotech.api.loki.LokiService
import com.cosmotech.scenariorun.ORGANIZATION_ID_LABEL
import com.cosmotech.scenariorun.SCENARIO_ID_LABEL
import com.cosmotech.scenariorun.WORKSPACE_ID_LABEL
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunContainerLogs
import com.cosmotech.scenariorun.domain.ScenarioRunLogs
import com.cosmotech.scenariorun.domain.ScenarioRunResourceRequested
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.domain.ScenarioRunState
import com.cosmotech.scenariorun.domain.ScenarioRunStatus
import com.cosmotech.scenariorun.domain.ScenarioRunStatusNode
import com.cosmotech.scenariorun.workflow.WorkflowContextData
import com.cosmotech.scenariorun.workflow.WorkflowService
import com.cosmotech.scenariorun.workflow.WorkflowStatusAndArtifact
import io.argoproj.workflow.ApiClient
import io.argoproj.workflow.ApiException
import io.argoproj.workflow.Configuration
import io.argoproj.workflow.apis.ArchivedWorkflowServiceApi
import io.argoproj.workflow.apis.InfoServiceApi
import io.argoproj.workflow.apis.WorkflowServiceApi
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1NodeStatus
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowCreateRequest
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowList
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowStatus
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowStopRequest
import java.lang.StringBuilder
import java.security.cert.X509Certificate
import java.time.Instant
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

@Service("argo")
@ConditionalOnExpression("#{! '\${csm.platform.argo.base-uri}'.trim().isEmpty()}")
@Suppress("TooManyFunctions")
internal class ArgoWorkflowService(
    @Value("\${api.version:?}") private val apiVersion: String,
    private val csmPlatformProperties: CsmPlatformProperties,
    private val lokiService: LokiService,
) : WorkflowService {

  private val logger = LoggerFactory.getLogger(ArgoWorkflowService::class.java)

  private val unsafeOkHttpClient: OkHttpClient by lazy {
    // Create a trust manager that does not validate certificate chains
    val trustAllCerts =
        object : X509TrustManager {
          override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            logger.trace("checkClientTrusted($authType)")
          }

          override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            logger.trace("checkServerTrusted($authType)")
          }

          override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
        }

    // Install the all-trusting trust manager
    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, arrayOf(trustAllCerts), java.security.SecureRandom())

    OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts)
        .hostnameVerifier { _, _ -> true }
        .build()
  }

  private val unsafeScalarRetrofit: Retrofit by lazy {
    Retrofit.Builder()
        .addConverterFactory(ScalarsConverterFactory.create())
        .baseUrl(csmPlatformProperties.argo.baseUri)
        .client(unsafeOkHttpClient)
        .build()
  }

  private val artifactsByUidService: ArgoArtifactsByUidService by lazy {
    this.unsafeScalarRetrofit.create(ArgoArtifactsByUidService::class.java)
  }

  private val apiClient: ApiClient by lazy {
    Configuration.getDefaultApiClient().apply {
      isVerifyingSsl = false
      basePath = csmPlatformProperties.argo.baseUri
      httpClient = unsafeOkHttpClient
      isDebugging = logger.isTraceEnabled
      setUserAgent("com.cosmotech/cosmotech-api $apiVersion")
      json =
          json.apply {
            gson =
                gson
                    .newBuilder()
                    .registerTypeAdapter(java.time.Instant::class.java, InstantTypeAdapter())
                    .create()
          }
    }
  }

  private fun getWorkflowStatus(
      workflowId: String,
      workflowName: String
  ): IoArgoprojWorkflowV1alpha1WorkflowStatus? {
    return try {
      this.getActiveWorkflow(workflowId, workflowName).status
    } catch (apiException: ApiException) {
      logger.warn("Could not retrieve status for workflow '{}' ({})", workflowName, workflowId)
      logger.trace(
          "Could not retrieve status for workflow '{}' ({})",
          workflowName,
          workflowId,
          apiException)
      null
    }
  }

  private fun getActiveWorkflow(
      workflowId: String,
      workflowName: String
  ): IoArgoprojWorkflowV1alpha1Workflow {
    var workflow: IoArgoprojWorkflowV1alpha1Workflow? = null
    try {
      // Workflows are auto-archived and auto-deleted more frequently
      // (as soon as they succeed or after a TTL).
      // Therefore, it is more likely to have more archived workflows.
      // So we are calling the ArchivedWorkflow API first, to reduce the number of round trips to
      // Argo
      workflow =
          newServiceApiInstance<ArchivedWorkflowServiceApi>(this.apiClient)
              .archivedWorkflowServiceGetArchivedWorkflow(workflowId)
      logger.trace("Workflow: {}", workflow)
    } catch (e: ApiException) {
      val logMessage =
          "Workflow $workflowName not found in the archived workflows - trying to find it in the active ones"
      logger.trace(logMessage, e)
    }
    if (workflow == null) {
      workflow =
          newServiceApiInstance<WorkflowServiceApi>(this.apiClient)
              .workflowServiceGetWorkflow(
                  csmPlatformProperties.argo.workflows.namespace, workflowName, "", "")!!
    }

    return workflow
  }

  private fun getWorkflowLogs(workflow: IoArgoprojWorkflowV1alpha1Workflow): Map<String, String> {
    val workflowId = workflow.metadata.uid
    val logsMap: MutableMap<String, String> = mutableMapOf()
    if (workflowId != null) {
      workflow.status?.nodes?.forEach { (nodeKey, nodeValue) ->
        nodeValue.outputs?.artifacts?.forEach {
          val artifactName = it.name ?: ""
          it.s3.let {
            val artifactLogs =
                artifactsByUidService
                    .getArtifactByUid(workflowId, nodeKey, artifactName)
                    .execute()
                    .body()
                    ?: ""
            logsMap[nodeKey] = artifactLogs
          }
        }
      }
    }
    return logsMap
  }

  private fun getCumulatedSortedLogs(
      nodes: Map<String, IoArgoprojWorkflowV1alpha1NodeStatus>,
      logsMap: Map<String, String>,
      child: String? = null
  ): String {
    val parents =
        nodes.filter { (_, node) ->
          if (child == null) node.children == null
          else node.children != null && child in node.children!!
        }
    var logs = ""
    parents.keys.forEach {
      logs += this.getCumulatedSortedLogs(nodes, logsMap, it)
      logs += logsMap[it] ?: ""
    }

    return logs
  }

  override fun launchScenarioRun(
      scenarioRunStartContainers: ScenarioRunStartContainers,
      executionTimeout: Int?,
      alwaysPull: Boolean
  ): ScenarioRun {
    val body =
        IoArgoprojWorkflowV1alpha1WorkflowCreateRequest()
            .workflow(
                buildWorkflow(
                    csmPlatformProperties,
                    scenarioRunStartContainers,
                    executionTimeout,
                    alwaysPull))

    logger.trace("Workflow: {}", body.workflow)

    try {
      val workflow =
          newServiceApiInstance<WorkflowServiceApi>(this.apiClient)
              .workflowServiceCreateWorkflow(csmPlatformProperties.argo.workflows.namespace, body)
      if (workflow.metadata.uid == null) {
        throw IllegalStateException("Argo Workflow metadata.uid is null")
      }
      if (workflow.metadata.name == null) {
        throw IllegalStateException("Argo Workflow metadata.name is null")
      }

      return ScenarioRun(
          csmSimulationRun = scenarioRunStartContainers.csmSimulationId,
          generateName = scenarioRunStartContainers.generateName,
          workflowId = workflow.metadata.uid,
          workflowName = workflow.metadata.name,
          nodeLabel = scenarioRunStartContainers.nodeLabel,
          containers = scenarioRunStartContainers.containers,
      )
    } catch (e: ApiException) {
      logger.warn(
          """
        Exception when calling WorkflowServiceApi#workflowServiceCreateWorkflow.
        Status code: ${e.code}
        Reason: ${e.responseBody}
        """
              .trimIndent())
      logger.debug("Response headers: {}", e.responseHeaders)
      throw IllegalStateException(e)
    }
  }

  override fun findWorkflowStatusAndArtifact(
      labelSelector: String,
      artifactNameFilter: String
  ): List<WorkflowStatusAndArtifact> {
    var workflowList: IoArgoprojWorkflowV1alpha1WorkflowList? =
        findWorkflowListByLabel(labelSelector)

    return workflowList?.items?.map { workflow ->
      val workflowId = workflow.metadata.uid!!
      val status = workflow.status?.phase
      val artifactContent = StringBuilder()
      // Listing Workflows does not return everything in each Workflow object =>
      // need to make an additional call to Argo via getActiveWorkflow()
      getActiveWorkflow(workflowId, workflow.metadata.name!!).status?.nodes?.forEach {
          (nodeKey, nodeValue) ->
        nodeValue.outputs
            ?.artifacts
            ?.filter { it.name == artifactNameFilter }
            ?.forEach {
              it.s3.let {
                artifactContent.append(
                    artifactsByUidService
                        .getArtifactByUid(workflowId, nodeKey, artifactNameFilter)
                        .execute()
                        .body()
                        ?: "")
              }
            }
      }
      WorkflowStatusAndArtifact(
          workflowId = workflowId, status = status, artifactContent = artifactContent.toString())
    }
        ?: listOf()
  }

  override fun findWorkflowStatusByLabel(
      labelSelector: String,
      skipArchive: Boolean,
  ): List<com.cosmotech.scenariorun.workflow.WorkflowStatus> {
    var workflowList: IoArgoprojWorkflowV1alpha1WorkflowList? =
        findWorkflowListByLabel(labelSelector, skipArchive)

    return workflowList?.items?.map { workflow ->
      val workflowId = workflow.metadata.uid!!
      val status = workflow.status?.phase
      val organizationId = workflow.metadata.labels?.getOrDefault(ORGANIZATION_ID_LABEL, "none")
      val workspaceId = workflow.metadata.labels?.getOrDefault(WORKSPACE_ID_LABEL, "none")
      val scenarioId = workflow.metadata.labels?.getOrDefault(SCENARIO_ID_LABEL, "none")
      com.cosmotech.scenariorun.workflow.WorkflowStatus(
          workflowId = workflowId,
          status = status,
          contextData = WorkflowContextData(organizationId, workspaceId, scenarioId))
    }
        ?: listOf()
  }

  internal fun findWorkflowListByLabel(
      labelSelector: String,
      skipArchive: Boolean = false,
  ): IoArgoprojWorkflowV1alpha1WorkflowList? {
    var workflowList: IoArgoprojWorkflowV1alpha1WorkflowList? = null
    if (!skipArchive) {
      try {
        // Workflows are auto-archived and auto-deleted more frequently
        // (as soon as they succeed or after a TTL).
        // Therefore, it is more likely to have more archived workflows.
        // So we are calling the ArchivedWorkflow API first, to reduce the number of round trips to
        // Argo
        workflowList =
            newServiceApiInstance<ArchivedWorkflowServiceApi>(this.apiClient)
                .archivedWorkflowServiceListArchivedWorkflows(
                    labelSelector, null, null, null, null, null, null, null, null, null)
        logger.trace("workflowList: {}", workflowList)
      } catch (e: ApiException) {
        val logMessage =
            "No archived workflow found for label selector $labelSelector - trying to find in the active ones"
        logger.debug(logMessage)
        logger.trace(logMessage, e)
      }
    }

    if (workflowList?.items?.isEmpty() != false) {
      workflowList =
          newServiceApiInstance<WorkflowServiceApi>(this.apiClient)
              .workflowServiceListWorkflows(
                  csmPlatformProperties.argo.workflows.namespace,
                  labelSelector,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null)!!
    }
    return workflowList
  }

  override fun getScenarioRunStatus(scenarioRun: ScenarioRun): ScenarioRunStatus {
    val scenarioRunId = scenarioRun.id
    val organizationId = scenarioRun.organizationId
    val workflowId = scenarioRun.workflowId
    val workflowName = scenarioRun.workflowName
    if (workflowId == null || workflowName == null) {
      throw IllegalStateException(
          "Scenario run $scenarioRunId for Organization $organizationId contains a null " +
              "workflowId or workflowName")
    }
    val workflowStatus = getWorkflowStatus(workflowId, workflowName)
    return buildScenarioRunStatusFromWorkflowStatus(scenarioRun, workflowStatus)
  }

  override fun getScenarioRunLogs(scenarioRun: ScenarioRun): ScenarioRunLogs {
    val workflowId = scenarioRun.workflowId
    val workflowName = scenarioRun.workflowName
    var containersLogs = mutableMapOf<String, ScenarioRunContainerLogs>()
    if (workflowId != null && workflowName != null) {
      val workflow = getActiveWorkflow(workflowId, workflowName)
      workflow.status?.nodes?.forEach { (nodeKey, nodeValue) ->
        containersLogs[nodeValue.displayName.toString()] =
            ScenarioRunContainerLogs(
                nodeId = nodeKey,
                containerName = nodeValue.displayName,
                children = nodeValue.children,
                logs =
                    lokiService.getPodLogs(
                        csmPlatformProperties.argo.workflows.namespace,
                        nodeKey,
                        nodeValue.startedAt ?: Instant.now()))
      }
    }
    return ScenarioRunLogs(scenariorunId = scenarioRun.id, containers = containersLogs)
  }

  override fun getScenarioRunCumulatedLogs(scenarioRun: ScenarioRun): String {
    val workflowId = scenarioRun.workflowId
    val workflowName = scenarioRun.workflowName
    return if (workflowId != null && workflowName != null) {
      val workflow = this.getActiveWorkflow(workflowId, workflowName)
      val logsMap = this.getWorkflowLogs(workflow)
      var cumulatedLogs = ""
      val nodes = workflow.status?.nodes
      if (nodes != null) {
        cumulatedLogs = this.getCumulatedSortedLogs(nodes, logsMap)
      }
      cumulatedLogs
    } else ""
  }

  @Suppress("TooGenericExceptionCaught")
  override fun health(): Health {
    val healthBuilder =
        try {
          if (newServiceApiInstance<InfoServiceApi>(this.apiClient).infoServiceGetInfo() != null) {
            Health.up()
          } else {
            Health.unknown().withDetail("detail", "Unknown Argo Server Info Response")
          }
        } catch (exception: Exception) {
          logger.debug("Error in health-check: {}", exception.message, exception)
          Health.down(exception)
        }
    return healthBuilder.withDetail("url", apiClient.basePath).build()
  }

  override fun stopWorkflow(scenarioRun: ScenarioRun): ScenarioRunStatus {
    var workflow = IoArgoprojWorkflowV1alpha1Workflow()
    try {
      workflow =
          newServiceApiInstance<WorkflowServiceApi>(this.apiClient)
              .workflowServiceStopWorkflow(
                  csmPlatformProperties.argo.workflows.namespace,
                  scenarioRun.workflowName,
                  IoArgoprojWorkflowV1alpha1WorkflowStopRequest())
    } catch (e: ApiException) {
      System.err.println("Exception when calling WorkflowServiceApi#workflowServiceStopWorkflow")
      System.err.println("Status code: " + e.code)
      System.err.println("Reason: " + e.responseBody)
      System.err.println("Response headers: " + e.responseHeaders)
    }

    return buildScenarioRunStatusFromWorkflowStatus(scenarioRun, workflow.status)
  }

  private fun buildScenarioRunStatusFromWorkflowStatus(
      scenarioRun: ScenarioRun,
      workflowStatus: IoArgoprojWorkflowV1alpha1WorkflowStatus?
  ): ScenarioRunStatus {
    return ScenarioRunStatus(
        id = scenarioRun.id,
        organizationId = scenarioRun.organizationId,
        workflowId = scenarioRun.workflowId,
        workflowName = scenarioRun.workflowName,
        startTime = workflowStatus?.startedAt?.toString(),
        endTime = workflowStatus?.finishedAt?.toString(),
        phase =
            if (scenarioRun.state == ScenarioRunState.Failed) ScenarioRunState.Failed.toString()
            else workflowStatus?.phase,
        progress = workflowStatus?.progress,
        message = workflowStatus?.message,
        estimatedDuration = workflowStatus?.estimatedDuration,
        nodes =
            workflowStatus?.nodes?.values?.map { nodeStatus ->
              ScenarioRunStatusNode(
                  id = nodeStatus.id,
                  name = nodeStatus.name,
                  containerName = nodeStatus.displayName,
                  estimatedDuration = nodeStatus.estimatedDuration,
                  resourcesDuration =
                      ScenarioRunResourceRequested(
                          nodeStatus.resourcesDuration?.get("cpu"),
                          nodeStatus.resourcesDuration?.get("memory")),
                  outboundNodes = nodeStatus.outboundNodes,
                  hostNodeName = nodeStatus.hostNodeName,
                  message = nodeStatus.message,
                  phase = nodeStatus.phase,
                  progress = nodeStatus.progress,
                  startTime = nodeStatus.startedAt?.toString(),
                  endTime = nodeStatus.finishedAt?.toString(),
              )
            })
  }

  // Should be handled synchronously
  @EventListener(WorkflowStatusRequest::class)
  fun onWorkflowStatusRequest(workflowStatusRequest: WorkflowStatusRequest) {
    workflowStatusRequest.response =
        getWorkflowStatus(workflowStatusRequest.workflowId, workflowStatusRequest.workflowName)
            ?.phase
  }
}

private inline fun <reified T> newServiceApiInstance(apiClient: ApiClient) =
    T::class.java.getDeclaredConstructor(ApiClient::class.java).newInstance(apiClient)
