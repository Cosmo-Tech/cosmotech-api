// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.argo

import com.cosmotech.api.retrofit.getArgoLogArtifact
import io.argoproj.workflow.Configuration
import io.argoproj.workflow.apis.ArchivedWorkflowServiceApi
import io.argoproj.workflow.models.Workflow

fun getArchiveWorkflow(workflowId: String): Workflow {
  val defaultClient = Configuration.getDefaultApiClient()
  defaultClient.setVerifyingSsl(false)
  defaultClient.setBasePath("https://argo-server.argo.svc.cluster.local:2746")

  val apiInstance = ArchivedWorkflowServiceApi(defaultClient)
  return apiInstance.archivedWorkflowServiceGetArchivedWorkflow(workflowId)
}

fun getCumulatedLogsById(workflowId: String): String {
  val workflow = getArchiveWorkflow(workflowId)
  return getCumulatedWorkflowLogs(workflow)
}

fun getCumulatedWorkflowLogs(workflow: Workflow): String {
  val namespace = "phoenix"
  val workflowName = workflow.metadata.name ?: ""
  var cumulatedLogs = ""
  workflow.status?.nodes?.forEach { (nodeKey, nodeValue) ->
    val nodeName = nodeValue.name ?: ""
    nodeValue.outputs?.artifacts?.forEach {
      if (it.s3 != null) {
        val artifactName = it.name ?: ""
        val artifactLogs = getArgoLogArtifact(namespace, workflowName, nodeName, artifactName)
        cumulatedLogs += artifactLogs
      }
    }
  }

  return cumulatedLogs
}

fun getWorkflowLogs(workflow: Workflow): Map<String, String> {
  TODO("Not implemented yet")
}
