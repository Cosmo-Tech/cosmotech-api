// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.workflow.argo

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.WorkflowStatusRequest
import com.cosmotech.api.loki.LokiService
import com.cosmotech.run.CONTAINER_CSM_ORC
import com.cosmotech.run.ORGANIZATION_ID_LABEL
import com.cosmotech.run.RUNNER_ID_LABEL
import com.cosmotech.run.WORKSPACE_ID_LABEL
import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunLogs
import com.cosmotech.run.domain.RunLogsEntry
import com.cosmotech.run.domain.RunResourceRequested
import com.cosmotech.run.domain.RunState
import com.cosmotech.run.domain.RunStatus
import com.cosmotech.run.domain.RunStatusNode
import com.cosmotech.run.workflow.RunStartContainers
import com.cosmotech.run.workflow.WorkflowContextData
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.run.workflow.WorkflowStatus
import io.argoproj.workflow.ApiClient
import io.argoproj.workflow.ApiException
import io.argoproj.workflow.Configuration
import io.argoproj.workflow.apis.ArchivedWorkflowServiceApi
import io.argoproj.workflow.apis.InfoServiceApi
import io.argoproj.workflow.apis.WorkflowServiceApi
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowCreateRequest
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowList
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowStatus
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowStopRequest
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

@Service("runArgo")
@ConditionalOnExpression("#{! '\${csm.platform.argo.base-uri}'.trim().isEmpty()}")
@Suppress("TooManyFunctions")
internal class RunArgoWorkflowService(
    @Value("\${api.version:?}") private val apiVersion: String,
    private val csmPlatformProperties: CsmPlatformProperties,
    private val lokiService: LokiService,
) : WorkflowService {

  private val logger = LoggerFactory.getLogger(RunArgoWorkflowService::class.java)

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

  override fun launchRun(
      runStartContainers: RunStartContainers,
      executionTimeout: Int?,
      alwaysPull: Boolean
  ): Run {
    val body =
        IoArgoprojWorkflowV1alpha1WorkflowCreateRequest()
            .workflow(
                buildWorkflow(
                    csmPlatformProperties, runStartContainers, executionTimeout, alwaysPull))

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

      return Run(
          csmSimulationRun = runStartContainers.csmSimulationId,
          generateName = runStartContainers.generateName,
          workflowId = workflow.metadata.uid,
          workflowName = workflow.metadata.name,
          nodeLabel = runStartContainers.nodeLabel,
          containers = runStartContainers.containers,
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

  override fun findWorkflowStatusByLabel(
      labelSelector: String,
      skipArchive: Boolean,
  ): List<WorkflowStatus> {
    var workflowList: IoArgoprojWorkflowV1alpha1WorkflowList? =
        findWorkflowListByLabel(labelSelector, skipArchive)

    return workflowList?.items?.map { workflow ->
      val workflowId = workflow.metadata.uid!!
      val status = workflow.status?.phase
      val organizationId = workflow.metadata.labels?.getOrDefault(ORGANIZATION_ID_LABEL, "none")
      val workspaceId = workflow.metadata.labels?.getOrDefault(WORKSPACE_ID_LABEL, "none")
      val runnerId = workflow.metadata.labels?.getOrDefault(RUNNER_ID_LABEL, "none")
      WorkflowStatus(
          workflowId = workflowId,
          status = status,
          contextData = WorkflowContextData(organizationId, workspaceId, runnerId))
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

  override fun getRunStatus(run: Run): RunStatus {
    val runId = run.id
    val organizationId = run.organizationId
    val workflowId = run.workflowId
    val workflowName = run.workflowName
    if (workflowId == null || workflowName == null) {
      throw IllegalStateException(
          "Scenario run $runId for Organization $organizationId contains a null " +
              "workflowId or workflowName")
    }
    val workflowStatus = getWorkflowStatus(workflowId, workflowName)
    return buildRunStatusFromWorkflowStatus(run, workflowStatus)
  }

  override fun getRunLogs(run: Run): RunLogs {
    val workflowId = run.workflowId
    val workflowName = run.workflowName
    var containersLogs = listOf<RunLogsEntry>()
    if (workflowId != null && workflowName != null) {
      getActiveWorkflow(workflowId, workflowName)
          .status
          ?.nodes
          ?.firstNotNullOf { (_, v) -> v.takeIf { it.displayName == CONTAINER_CSM_ORC } }
          ?.let {
            containersLogs =
                lokiService
                    .getPodLogs(
                        csmPlatformProperties.argo.workflows.namespace,
                        it.id,
                        it.startedAt ?: Instant.now())
                    .map { RunLogsEntry(line = it) }
          }
    }
    return RunLogs(runId = run.id!!, logs = containersLogs)
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

  override fun stopWorkflow(run: Run): RunStatus {
    var workflow = IoArgoprojWorkflowV1alpha1Workflow()
    try {
      workflow =
          newServiceApiInstance<WorkflowServiceApi>(this.apiClient)
              .workflowServiceStopWorkflow(
                  csmPlatformProperties.argo.workflows.namespace,
                  run.workflowName,
                  IoArgoprojWorkflowV1alpha1WorkflowStopRequest())
    } catch (e: ApiException) {
      logger.error("Exception when calling WorkflowServiceApi#workflowServiceStopWorkflow")
      logger.error("Status code: " + e.code)
      logger.error("Reason: " + e.responseBody)
      logger.error("Response headers: " + e.responseHeaders)
    }

    return buildRunStatusFromWorkflowStatus(run, workflow.status)
  }

  private fun buildRunStatusFromWorkflowStatus(
      run: Run,
      workflowStatus: IoArgoprojWorkflowV1alpha1WorkflowStatus?
  ): RunStatus {
    return RunStatus(
        id = run.id,
        organizationId = run.organizationId,
        workspaceId = run.workspaceId,
        runnerId = run.runnerId,
        workflowId = run.workflowId,
        workflowName = run.workflowName,
        startTime = workflowStatus?.startedAt?.toString(),
        endTime = workflowStatus?.finishedAt?.toString(),
        phase =
            if (run.state == RunState.Failed) RunState.Failed.toString() else workflowStatus?.phase,
        progress = workflowStatus?.progress,
        message = workflowStatus?.message,
        estimatedDuration = workflowStatus?.estimatedDuration,
        nodes =
            workflowStatus?.nodes?.values?.map { nodeStatus ->
              RunStatusNode(
                  id = nodeStatus.id,
                  name = nodeStatus.name,
                  containerName = nodeStatus.displayName,
                  estimatedDuration = nodeStatus.estimatedDuration,
                  resourcesDuration =
                      RunResourceRequested(
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
