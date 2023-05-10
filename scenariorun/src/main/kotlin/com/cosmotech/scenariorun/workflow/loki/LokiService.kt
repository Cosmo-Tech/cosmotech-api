// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow.loki

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunContainerLogs
import com.cosmotech.scenariorun.domain.ScenarioRunLogs
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.springframework.stereotype.Service

@Service("loki")
internal class LokiService(
    private val csmPlatformProperties: CsmPlatformProperties,
) {
  private fun getLokiQueryURI(): String {
    val baseURI = "http://localhost:3100"
    val postfixURI = "/loki/api/v1/query"
    return baseURI + postfixURI
  }

  private fun getHttpURLConnection(namespace: String, podName: String): HttpURLConnection {
    val url = URL(getLokiQueryURI())
    val httpConn: HttpURLConnection = url.openConnection() as HttpURLConnection
    httpConn.setRequestMethod("GET")

    httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

    httpConn.setDoOutput(true)
    val writer = OutputStreamWriter(httpConn.getOutputStream())
    var query = String.format("query={namespace=\"%s\", pod=\"%s\"}", namespace, podName)
    writer.write(query)
    writer.flush()
    writer.close()
    httpConn.getOutputStream().close()
    return httpConn
  }

  fun getScenarioRunLogs(
      scenarioRun: ScenarioRun,
      workflow: IoArgoprojWorkflowV1alpha1Workflow
  ): ScenarioRunLogs {
    var containersLogs: Map<String, ScenarioRunContainerLogs> = mapOf()

    if (workflow.metadata.uid != null) {
      workflow.status?.nodes?.forEach { (nodeKey, nodeValue) ->
        var name = StringBuilder(nodeValue.id)
        var toInsert = nodeValue.displayName + "-"
        name.insert(nodeValue.id.lastIndexOf('-') + 1, toInsert)
        val httpConn =
            getHttpURLConnection(csmPlatformProperties.argo.workflows.namespace, name.toString())
        //      val responseStream: InputStream =
        //          if (httpConn.getResponseCode() / 100 === 2) httpConn.getInputStream() else
        // httpConn.getErrorStream()
        //      val s: Scanner = Scanner(responseStream).useDelimiter("\\A")
        //      val response = if (s.hasNext()) s.next() else ""
        name.clear()
      }
    }

    return ScenarioRunLogs(scenariorunId = scenarioRun.id, containers = containersLogs)
  }
}
