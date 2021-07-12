// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow.argo

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.WorkflowStatusRequest
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunContainerLogs
import com.cosmotech.scenariorun.domain.ScenarioRunLogs
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.domain.ScenarioRunStatus
import com.cosmotech.scenariorun.domain.ScenarioRunStatusNode
import com.cosmotech.scenariorun.workflow.WorkflowService
import io.argoproj.workflow.ApiClient
import io.argoproj.workflow.ApiException
import io.argoproj.workflow.Configuration
import io.argoproj.workflow.apis.ArchivedWorkflowServiceApi
import io.argoproj.workflow.apis.InfoServiceApi
import io.argoproj.workflow.apis.WorkflowServiceApi
import io.argoproj.workflow.models.DAGTask
import io.argoproj.workflow.models.DAGTemplate
import io.argoproj.workflow.models.Metadata
import io.argoproj.workflow.models.NodeStatus
import io.argoproj.workflow.models.Template
import io.argoproj.workflow.models.Workflow
import io.argoproj.workflow.models.WorkflowCreateRequest
import io.argoproj.workflow.models.WorkflowSpec
import io.argoproj.workflow.models.WorkflowStatus
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1Container
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1LocalObjectReference
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import io.kubernetes.client.openapi.models.V1VolumeMount
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

private const val CSM_DAG_ENTRYPOINT = "entrypoint"
private const val CSM_DEFAULT_WORKFLOW_NAME = "default-workflow-"
private const val VOLUME_CLAIM_DATASETS = "datasetsdir"
private const val VOLUME_CLAIM_PARAMETERS = "parametersdir"
private const val VOLUME_DATASETS_PATH = "/mnt/scenariorun-data"
private const val VOLUME_PARAMETERS_PATH = "/mnt/scenariorun-parameters"

@Service("argo")
@ConditionalOnExpression("#{! '\${csm.platform.argo.base-uri}'.trim().isEmpty()}")
internal class ArgoWorkflowService(
    @Value("\${api.version:?}") private val apiVersion: String,
    private val csmPlatformProperties: CsmPlatformProperties,
) : WorkflowService {

  private val logger = LoggerFactory.getLogger(ArgoWorkflowService::class.java)

  private val workflowImagePullSecrets =
      csmPlatformProperties
          .argo
          .imagePullSecrets
          ?.filterNot(String::isBlank)
          ?.map(V1LocalObjectReference()::name)

  private val unsafeOkHttpClient: OkHttpClient by lazy {
    // Create a trust manager that does not validate certificate chains
    val trustAllCerts =
        object : X509TrustManager {
          override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

          override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

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

  private val artifactsService: ArgoArtifactsService by lazy {
    this.unsafeScalarRetrofit.create(ArgoArtifactsService::class.java)
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
    }
  }

  private fun getLogArtifact(namespace: String, workflow: String, node: String, artifact: String) =
      artifactsService.getArtifact(namespace, workflow, node, artifact).execute().body() ?: ""

  private fun getLogArtifactByUid(workflowId: String, node: String, artifact: String) =
      artifactsByUidService.getArtifactByUid(workflowId, node, artifact).execute().body() ?: ""

  private fun getArchiveWorkflow(workflowId: String): Workflow {
    return newServiceApiInstance<ArchivedWorkflowServiceApi>()
        .archivedWorkflowServiceGetArchivedWorkflow(workflowId)
  }

  private fun getWorkflowStatus(workflowId: String, workflowName: String): WorkflowStatus? {
    return try {
      this.getActiveWorkflow(workflowId, workflowName).status
    } catch (apiException: ApiException) {
      logger.warn(
          "Could not retrieve status for workflow '{}' ({})",
          workflowName,
          workflowId,
          apiException)
      null
    }
  }

  private fun startWorkflow(scenarioRunStartContainers: ScenarioRunStartContainers): Workflow {

    val body = WorkflowCreateRequest()

    val workflow = buildWorkflow(scenarioRunStartContainers)
    logger.trace("Workflow: {}", workflow)
    body.workflow(workflow)

    try {
      val result =
          newServiceApiInstance<WorkflowServiceApi>()
              .workflowServiceCreateWorkflow(csmPlatformProperties.argo.workflows.namespace, body)
      if (result.metadata.uid == null)
          throw IllegalStateException("Argo Workflow metadata.uid is null")
      if (result.metadata.name == null)
          throw IllegalStateException("Argo Workflow metadata.name is null")

      return result
    } catch (e: ApiException) {
      logger.warn(
          """
        Exception when calling WorkflowServiceApi#workflowServiceCreateWorkflow.
        Status code: ${e.code}
        Reason: ${e.responseBody}
      """.trimIndent())
      logger.debug("Response headers: {}", e.responseHeaders)
      throw IllegalStateException(e)
    }
  }

  private fun getWorkflow(workflowName: String): Workflow {
    return newServiceApiInstance<WorkflowServiceApi>()
        .workflowServiceGetWorkflow(
            csmPlatformProperties.argo.workflows.namespace, workflowName, "", "")
  }

  private fun getCumulatedLogs(workflowId: String, workflowName: String): String {
    val workflow = this.getActiveWorkflow(workflowId, workflowName)
    return getCumulatedWorkflowLogs(workflow)
  }

  private fun getActiveWorkflow(workflowId: String, workflowName: String): Workflow {
    var workflow: Workflow? = null
    try {
      workflow = this.getWorkflow(workflowName)
      logger.trace("Workflow: {}", workflow)
    } catch (e: ApiException) {
      logger.debug("Workflow $workflowName not found, trying to find it in archive", e)
    }
    if (workflow == null) {
      workflow = this.getArchiveWorkflow(workflowId)
    }

    return workflow
  }

  private fun getCumulatedWorkflowLogs(workflow: Workflow): String {
    val logsMap = this.getWorkflowLogs(workflow)
    var cumulatedLogs = ""
    val nodes = workflow.status?.nodes
    if (nodes != null) {
      cumulatedLogs = this.getCumulatedSortedLogs(nodes, logsMap)
    }

    return cumulatedLogs
  }

  private fun getWorkflowLogs(workflow: Workflow): Map<String, String> {
    val workflowId = workflow.metadata.uid
    val logsMap: MutableMap<String, String> = mutableMapOf()
    if (workflowId != null) {
      workflow.status?.nodes?.forEach { (nodeKey, nodeValue) ->
        nodeValue.outputs?.artifacts?.forEach {
          if (it.s3 != null) {
            val artifactName = it.name ?: ""
            val artifactLogs = getLogArtifactByUid(workflowId, nodeKey, artifactName)
            logsMap[nodeKey] = artifactLogs
          }
        }
      }
    }
    return logsMap
  }

  private fun getCumulatedSortedLogs(
      nodes: Map<String, NodeStatus>,
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

  internal fun buildTemplate(scenarioRunContainer: ScenarioRunContainer): Template {
    var envVars: MutableList<V1EnvVar>? = null
    if (scenarioRunContainer.envVars != null) {
      envVars = mutableListOf()
      scenarioRunContainer.envVars.forEach { (key, value) ->
        val envVar = V1EnvVar().name(key).value(value)
        envVars.add(envVar)
      }
    }
    val volumeMounts =
        listOf(
            V1VolumeMount().name(VOLUME_CLAIM_DATASETS).mountPath(VOLUME_DATASETS_PATH),
            V1VolumeMount().name(VOLUME_CLAIM_PARAMETERS).mountPath(VOLUME_PARAMETERS_PATH))

    val container =
        V1Container()
            .image(scenarioRunContainer.image)
            .env(envVars)
            .args(scenarioRunContainer.runArgs)
            .volumeMounts(volumeMounts)
    if (scenarioRunContainer.entrypoint != null) {
      container.command(listOf(scenarioRunContainer.entrypoint))
    }

    return Template()
        .name(scenarioRunContainer.name)
        .metadata(Metadata().labels(scenarioRunContainer.labels))
        .container(container)
  }

  internal fun buildWorkflowSpec(startContainers: ScenarioRunStartContainers): WorkflowSpec {
    val nodeSelector = buildNodeSelector(startContainers)
    val templates = buildContainersTemplates(startContainers)
    val entrypointTemplate = buildEntrypointTemplate(startContainers)
    templates.add(entrypointTemplate)
    val volumeClaims = buildVolumeClaims()

    return WorkflowSpec()
        .imagePullSecrets(workflowImagePullSecrets?.ifEmpty { null })
        .nodeSelector(nodeSelector)
        .serviceAccountName(csmPlatformProperties.argo.workflows.serviceAccountName)
        .entrypoint(CSM_DAG_ENTRYPOINT)
        .templates(templates)
        .volumeClaimTemplates(volumeClaims)
  }

  internal fun buildWorkflow(startContainers: ScenarioRunStartContainers): Workflow {
    val spec = buildWorkflowSpec(startContainers)
    val metadata = buildMetadata(startContainers)
    return Workflow().metadata(metadata).spec(spec)
  }

  override fun launchScenarioRun(
      scenarioRunStartContainers: ScenarioRunStartContainers
  ): ScenarioRun {
    val workflow = startWorkflow(scenarioRunStartContainers)
    return ScenarioRun(
        csmSimulationRun = scenarioRunStartContainers.csmSimulationId,
        generateName = scenarioRunStartContainers.generateName,
        workflowId = workflow.metadata.uid,
        workflowName = workflow.metadata.name,
        nodeLabel = scenarioRunStartContainers.nodeLabel,
        containers = scenarioRunStartContainers.containers,
    )
  }

  override fun getScenarioRunStatus(scenarioRun: ScenarioRun): ScenarioRunStatus {
    val scenarioRunId = scenarioRun.id
    val organizationId = scenarioRun.organizationId
    val workflowId = scenarioRun.workflowId
    val workflowName = scenarioRun.workflowName
    if (workflowId == null || workflowName == null) {
      throw IllegalStateException(
          "Scenario run $scenarioRunId for Organization $organizationId contains a null workflowId or workflowName")
    }
    val workflowStatus = getWorkflowStatus(workflowId, workflowName)
    return ScenarioRunStatus(
        id = scenarioRunId,
        organizationId = organizationId,
        workflowId = workflowId,
        workflowName = workflowName,
        startTime = workflowStatus?.startedAt?.toString(),
        endTime = workflowStatus?.finishedAt?.toString(),
        phase = workflowStatus?.phase,
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
                  hostNodeName = nodeStatus.hostNodeName,
                  message = nodeStatus.message,
                  phase = nodeStatus.phase,
                  progress = nodeStatus.progress,
                  startTime = nodeStatus.startedAt?.toString(),
                  endTime = nodeStatus.finishedAt?.toString(),
              )
            })
  }

  override fun getScenarioRunLogs(scenarioRun: ScenarioRun): ScenarioRunLogs {
    val workflowId = scenarioRun.workflowId
    val workflowName = scenarioRun.workflowName
    var containersLogs: Map<String, ScenarioRunContainerLogs> = mapOf()
    if (workflowId != null && workflowName != null) {
      val workflow = getActiveWorkflow(workflowId, workflowName)
      val nodeLogs = getWorkflowLogs(workflow)
      containersLogs =
          nodeLogs
              .map { (nodeId, logs) ->
                (workflow.status?.nodes?.get(nodeId)?.displayName
                    ?: "") to
                    ScenarioRunContainerLogs(
                        nodeId = nodeId,
                        containerName = workflow.status?.nodes?.get(nodeId)?.displayName,
                        children = workflow.status?.nodes?.get(nodeId)?.children,
                        logs = logs)
              }
              .toMap()
    }
    return ScenarioRunLogs(scenariorunId = scenarioRun.id, containers = containersLogs)
  }

  override fun getScenarioRunCumulatedLogs(scenarioRun: ScenarioRun): String {
    val workflowId = scenarioRun.workflowId
    val workflowName = scenarioRun.workflowName
    return if (workflowId != null && workflowName != null)
        getCumulatedLogs(workflowId, workflowName)
    else ""
  }

  override fun health(): Health {
    val healthBuilder =
        try {
          if (newServiceApiInstance<InfoServiceApi>().infoServiceGetInfo() != null) {
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

  private fun buildNodeSelector(startContainers: ScenarioRunStartContainers): Map<String, String> {
    val nodeSelector = mutableMapOf("kubernetes.io/os" to "linux")

    if (startContainers.nodeLabel != null) {
      nodeSelector[csmPlatformProperties.argo.workflows.nodePoolLabel] = startContainers.nodeLabel
    }

    return nodeSelector
  }

  private fun buildContainersTemplates(
      startContainers: ScenarioRunStartContainers
  ): MutableList<Template> {
    val list = startContainers.containers.map { container -> buildTemplate(container) }
    return list.toMutableList()
  }

  private fun buildEntrypointTemplate(startContainers: ScenarioRunStartContainers): Template {
    val dagTemplate = Template().name(CSM_DAG_ENTRYPOINT)
    val dagTasks: MutableList<DAGTask> = mutableListOf()
    var previousContainer: ScenarioRunContainer? = null
    for (container in startContainers.containers) {
      var dependencies: List<String>? = null
      if (container.dependencies != null) dependencies = container.dependencies
      else {
        if (previousContainer != null) dependencies = listOf(previousContainer.name)
      }
      val task = DAGTask().name(container.name).template(container.name).dependencies(dependencies)
      dagTasks.add(task)
      previousContainer = container
    }

    dagTemplate.dag(DAGTemplate().tasks(dagTasks))

    return dagTemplate
  }

  private fun buildVolumeClaims(): List<V1PersistentVolumeClaim> {
    val datasetsdir =
        V1PersistentVolumeClaim()
            .metadata(V1ObjectMeta().name(VOLUME_CLAIM_DATASETS))
            .spec(
                V1PersistentVolumeClaimSpec()
                    .accessModes(listOf("ReadWriteOnce"))
                    .resources(
                        V1ResourceRequirements().requests(mapOf("storage" to Quantity("1Gi")))))
    val parametersdir =
        V1PersistentVolumeClaim()
            .metadata(V1ObjectMeta().name(VOLUME_CLAIM_PARAMETERS))
            .spec(
                V1PersistentVolumeClaimSpec()
                    .accessModes(listOf("ReadWriteOnce"))
                    .resources(
                        V1ResourceRequirements().requests(mapOf("storage" to Quantity("1Gi")))))

    return listOf(datasetsdir, parametersdir)
  }

  private fun buildMetadata(startContainers: ScenarioRunStartContainers): V1ObjectMeta {
    val generateName = startContainers.generateName ?: CSM_DEFAULT_WORKFLOW_NAME
    return V1ObjectMeta().generateName(generateName)
  }

  // Should be handled synchronously
  @EventListener(WorkflowStatusRequest::class)
  fun onWorkflowStatusRequest(workflowStatusRequest: WorkflowStatusRequest) {
    val phase =
        getWorkflowStatus(workflowStatusRequest.workflowId, workflowStatusRequest.workflowName)
            ?.phase
    workflowStatusRequest.response = phase
  }

  private inline fun <reified T> newServiceApiInstance() =
      T::class.java.getDeclaredConstructor(ApiClient::class.java).newInstance(this.apiClient)
}

internal interface ArgoArtifactsService {
  @GET("artifacts/{namespace}/{workflow}/{node}/{artifact}")
  fun getArtifact(
      @Path("namespace") namespace: String,
      @Path("workflow") workflow: String,
      @Path("node") node: String,
      @Path("artifact") artifact: String
  ): Call<String>
}

internal interface ArgoArtifactsByUidService {
  @GET("artifacts-by-uid/{uid}/{node}/{artifact}")
  fun getArtifactByUid(
      @Path("uid") uid: String,
      @Path("node") node: String,
      @Path("artifact") artifact: String
  ): Call<String>
}
