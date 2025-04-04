// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.workflow.argo

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.WorkflowStatusRequest
import com.cosmotech.run.CONTAINER_CSM_ORC
import com.cosmotech.run.ORGANIZATION_ID_LABEL
import com.cosmotech.run.RUNNER_ID_LABEL
import com.cosmotech.run.WORKSPACE_ID_LABEL
import com.cosmotech.run.domain.Run
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
import io.argoproj.workflow.apis.ArtifactServiceApi
import io.argoproj.workflow.apis.InfoServiceApi
import io.argoproj.workflow.apis.WorkflowServiceApi
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowCreateRequest
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowStatus
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowStopRequest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service("runArgo")
@ConditionalOnExpression("#{! '\${csm.platform.argo.base-uri}'.trim().isEmpty()}")
@Suppress("TooManyFunctions")
internal class RunArgoWorkflowService(
    @Value("\${api.version:?}") private val apiVersion: String,
    private val csmPlatformProperties: CsmPlatformProperties,
) : WorkflowService {

  private val logger = LoggerFactory.getLogger(RunArgoWorkflowService::class.java)
  private val argoClient = RestClient.builder().baseUrl(csmPlatformProperties.argo.baseUri).build()

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

  private fun getWorkflowStatus(workflowName: String): IoArgoprojWorkflowV1alpha1WorkflowStatus? {
    return try {
      this.getActiveWorkflow(workflowName)?.status
    } catch (apiException: ApiException) {
      logger.warn("Could not retrieve status for workflow '{}'", workflowName, apiException)
      null
    }
  }

  private fun getActiveWorkflow(workflowName: String): IoArgoprojWorkflowV1alpha1Workflow? {
    var workflow: IoArgoprojWorkflowV1alpha1Workflow? = null
    try {
      workflow =
          newServiceApiInstance<WorkflowServiceApi>(this.apiClient)
              .workflowServiceGetWorkflow(
                  csmPlatformProperties.argo.workflows.namespace, workflowName, "", "")
    } catch (e: ApiException) {
      logger.warn(
          """
        Exception when calling WorkflowServiceApi#workflowServiceCreateWorkflow.
        Status code: ${e.code}
        Reason: ${e.responseBody}
        """
              .trimIndent())
      logger.debug("Response headers: " + e.getResponseHeaders())
    }

    return workflow
  }

  override fun launchRun(
      organizationId: String,
      workspaceId: String?,
      runStartContainers: RunStartContainers,
      executionTimeout: Int?,
      alwaysPull: Boolean
  ): Run {
    val body =
        IoArgoprojWorkflowV1alpha1WorkflowCreateRequest()
            .workflow(
                buildWorkflow(
                    organizationId,
                    workspaceId,
                    csmPlatformProperties,
                    runStartContainers,
                    executionTimeout,
                    alwaysPull))

    logger.debug("Workflow: {}", body.workflow)

    try {
      val workflow =
          newServiceApiInstance<WorkflowServiceApi>(this.apiClient)
              .workflowServiceCreateWorkflow(csmPlatformProperties.argo.workflows.namespace, body)
      checkNotNull(workflow.metadata.uid) { "Argo Workflow metadata.uid is null" }
      checkNotNull(workflow.metadata.name) { "Argo Workflow metadata.name is null" }

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
  ): List<WorkflowStatus> {
    val workflowList = findWorkflowListByLabel(labelSelector)

    return workflowList.map { workflow ->
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
  }

  internal fun findWorkflowListByLabel(
      labelSelector: String,
  ): List<IoArgoprojWorkflowV1alpha1Workflow> {

    return newServiceApiInstance<WorkflowServiceApi>(this.apiClient)
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
            null)
        .items
  }

  override fun getRunStatus(run: Run): RunStatus {
    val runId = run.id
    val organizationId = run.organizationId
    val workflowName =
        run.workflowName
            ?: throw IllegalStateException(
                "Run $runId for Organization $organizationId contains a null workflowName")
    val workflowStatus = getWorkflowStatus(workflowName)
    return buildRunStatusFromWorkflowStatus(run, workflowStatus)
  }

  override fun getRunningLogs(run: Run): String {
    val workflowName = run.workflowName
    var result = ""
    if (workflowName != null) {
      getActiveWorkflow(workflowName)
          ?.status
          ?.nodes
          ?.firstNotNullOf { (_, v) -> v.takeIf { it.displayName == CONTAINER_CSM_ORC } }
          ?.let { node ->
            val podName =
                node.id.substringBeforeLast("-") +
                    "-csmorchestrator-" +
                    node.id.substringAfterLast("-")
            val lines =
                argoClient
                    .get()
                    .uri(
                        "/api/v1/workflows/${csmPlatformProperties.argo.workflows.namespace}" +
                            "/$workflowName/log?podName=${podName}&logOptions.container=main")
                    .retrieve()
                    .body(String::class.java)
            lines?.split("\n")?.forEach {
              if (it.isNotEmpty()) {
                result += JSONObject(it).getJSONObject("result").optString("content", "") + "\n"
              }
            }
          }
    }
    return result
  }

  override fun getArchivedLogs(run: Run): String {
    val workflowName = run.workflowName
    var result = ""
    if (workflowName != null) {
      getActiveWorkflow(workflowName)
          ?.status
          ?.nodes
          ?.firstNotNullOf { (_, v) -> v.takeIf { it.displayName == CONTAINER_CSM_ORC } }
          ?.let {
            result =
                newServiceApiInstance<ArtifactServiceApi>(this.apiClient)
                    .artifactServiceGetOutputArtifactByUID(run.workflowId, it.id, "main-logs")
                    .readText()
          }
    }
    return result
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
      logger.warn(
          """
        Exception when calling WorkflowServiceApi#workflowServiceStopWorkflow.
        Status code: ${e.code}
        Reason: ${e.responseBody}
        """
              .trimIndent())
      logger.debug("Response headers: " + e.responseHeaders)
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
    workflowStatusRequest.response = getWorkflowStatus(workflowStatusRequest.workflowName)?.phase
  }
}

private inline fun <reified T> newServiceApiInstance(apiClient: ApiClient) =
    T::class.java.getDeclaredConstructor(ApiClient::class.java).newInstance(apiClient)
