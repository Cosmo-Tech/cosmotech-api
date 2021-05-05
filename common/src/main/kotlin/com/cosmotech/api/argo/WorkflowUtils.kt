// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.argo

import io.argoproj.workflow.ApiClient
import io.argoproj.workflow.ApiException
import io.argoproj.workflow.Configuration
import io.argoproj.workflow.apis.ArchivedWorkflowServiceApi
import io.argoproj.workflow.apis.WorkflowServiceApi
import io.argoproj.workflow.models.Workflow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class WorkflowUtils(
    val argoRetrofit: ArgoRetrofit,
    @Value("\${csm.platform.argo.base-url:}") val baseUrl: String
) {
  private val logger = LoggerFactory.getLogger(WorkflowUtils::class.java)

  fun getApiClient(): ApiClient {
    val apiClient = Configuration.getDefaultApiClient()
    apiClient.setVerifyingSsl(false)
    apiClient.setBasePath(baseUrl)
    return apiClient
  }

  fun getArchiveWorkflow(workflowId: String): Workflow {
    val apiClient = getApiClient()
    val apiInstance = ArchivedWorkflowServiceApi(apiClient)
    return apiInstance.archivedWorkflowServiceGetArchivedWorkflow(workflowId)
  }

  fun getWorkflow(workflowName: String): Workflow {
    val apiClient = getApiClient()
    val apiInstance = WorkflowServiceApi(apiClient)
    return apiInstance.workflowServiceGetWorkflow("phoenix", workflowName, "", "")
  }

  fun getCumulatedLogs(workflowId: String, workflowName: String): String {
    var workflow: Workflow? = null
    try {
      workflow = getWorkflow(workflowName)
    } catch (e: ApiException) {
      println("Workflow $workflowName not found, trying to find it in archive")
    }
    if (workflow == null) {
      workflow = getArchiveWorkflow(workflowId)
    }

    return getCumulatedWorkflowLogs(workflow)
  }

  fun getCumulatedWorkflowLogs(workflow: Workflow): String {
    val workflowId = workflow.metadata.uid
    var cumulatedLogs = ""
    if (workflowId != null) {
      workflow.status?.nodes?.forEach { (nodeKey, nodeValue) ->
        val nodeName = nodeValue.name ?: ""
        nodeValue.outputs?.artifacts?.forEach {
          if (it.s3 != null) {
            val artifactName = it.name ?: ""
            val artifactLogs = argoRetrofit.getLogArtifactByUid(workflowId, nodeName, artifactName)
            cumulatedLogs += artifactLogs
          }
        }
      }
    }

    return cumulatedLogs
  }

  fun getWorkflowLogs(workflow: Workflow): Map<String, String> {
    TODO("Not implemented yet")
  }
}
