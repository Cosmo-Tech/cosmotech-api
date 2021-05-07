// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.argo

import io.argoproj.workflow.ApiClient
import io.argoproj.workflow.ApiException
import io.argoproj.workflow.Configuration
import io.argoproj.workflow.apis.ArchivedWorkflowServiceApi
import io.argoproj.workflow.apis.WorkflowServiceApi
import io.argoproj.workflow.models.NodeStatus
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
    val apiClient = this.getApiClient()
    val apiInstance = ArchivedWorkflowServiceApi(apiClient)
    return apiInstance.archivedWorkflowServiceGetArchivedWorkflow(workflowId)
  }

  fun getWorkflow(workflowName: String): Workflow {
    val apiClient = this.getApiClient()
    val apiInstance = WorkflowServiceApi(apiClient)
    return apiInstance.workflowServiceGetWorkflow("phoenix", workflowName, "", "")
  }

  fun getCumulatedLogs(workflowId: String, workflowName: String): String {
    val workflow = this.getActiveWorkflow(workflowId, workflowName)
    return getCumulatedWorkflowLogs(workflow)
  }

  fun getActiveWorkflow(workflowId: String, workflowName: String): Workflow {
    var workflow: Workflow? = null
    try {
      workflow = this.getWorkflow(workflowName)
      logger.debug(workflow.toString())
    } catch (e: ApiException) {
      println("Workflow $workflowName not found, trying to find it in archive")
    }
    if (workflow == null) {
      workflow = this.getArchiveWorkflow(workflowId)
    }

    return workflow
  }

  fun getCumulatedWorkflowLogs(workflow: Workflow): String {
    val logsMap = this.getWorkflowLogs(workflow)
    var cumulatedLogs = ""
    val nodes = workflow.status?.nodes
    if (nodes != null) {
      cumulatedLogs = this.getCumulatedSortedLogs(nodes, logsMap)
    }

    return cumulatedLogs
  }

  fun getWorkflowLogs(workflow: Workflow): Map<String, String> {
    val workflowId = workflow.metadata.uid
    var logsMap: MutableMap<String, String> = mutableMapOf()
    if (workflowId != null) {
      workflow.status?.nodes?.forEach { (nodeKey, nodeValue) ->
        nodeValue.outputs?.artifacts?.forEach {
          if (it.s3 != null) {
            val artifactName = it.name ?: ""
            val artifactLogs = argoRetrofit.getLogArtifactByUid(workflowId, nodeKey, artifactName)
            logsMap.put(nodeKey, artifactLogs)
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
        nodes.filter { (key, node) ->
          if (child == null) node.children == null
          else node.children != null && child in node.children!!
        }
    var logs = ""
    parents.keys.forEach {
      logs += this.getCumulatedSortedLogs(nodes, logsMap, it)
      logs += logsMap.get(it) ?: ""
    }

    return logs
  }
}
