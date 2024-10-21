// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.metrics

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.PersistentMetricEvent
import com.cosmotech.api.events.RunStart
import com.cosmotech.api.metrics.DownSamplingAggregationType
import com.cosmotech.api.metrics.PersistentMetric
import com.cosmotech.api.metrics.PersitentMetricType
import com.cosmotech.run.WORKFLOW_TYPE_LABEL
import com.cosmotech.run.service.WORKFLOW_TYPE_RUN
import com.cosmotech.run.workflow.WorkflowContextData
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.runner.domain.Runner
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private const val RUNNING_STATUS = "Running"

private const val SERVICE_NAME = "run"

private const val ORGANIZATION_ID_LABEL = "organizationId"
private const val WORKSPACE_ID_LABEL = "workspaceId"
private const val RUNNER_ID_LABEL = "runnerId"

private const val DEFAULT_EMPTY_WORKFLOW_LABEL = "none"
private const val TOTAL_QUALIFIER = "total"
private const val TS_RUNNING_WORKFLOW_NAME = "running"
private const val TS_RUN_WORKFLOW_NAME = "run"

@Service
internal class RunMetrics(
    private val workflowService: WorkflowService,
    private val eventPublisher: CsmEventPublisher,
    private val csmPlatformProperties: CsmPlatformProperties,
) {

  @Scheduled(fixedDelay = 10000)
  fun publishCurrentRunningRunner() {
    if (!csmPlatformProperties.metrics.enabled) return

    val countRunningWorkflows = getRunningWorkflowsCount()
    val labels =
        mutableMapOf(
            "usage" to "licensing",
        )
    // Global
    publishRunningLabeledMetric(
        countRunningWorkflows.values.sum(), TS_RUNNING_WORKFLOW_NAME, labels)

    // By Organization, Workspace, Runner
    countRunningWorkflows.forEach { (workflowContext, workflowCount) ->
      publishOrganizationWorkspaceAndRunnerRunningLabeledMetricIfAny(
          workflowContext!!, workflowCount, TS_RUNNING_WORKFLOW_NAME, labels)
    }
  }

  @EventListener(RunStart::class)
  fun onRunStart(event: RunStart) {
    if (!csmPlatformProperties.metrics.enabled) return
    val runner = event.runnerData as Runner
    val labels =
        mutableMapOf(
            "usage" to "licensing",
        )
    var name = TS_RUN_WORKFLOW_NAME

    // Global
    publishRunStartLabeledMetric(name, labels)

    // By Organization
    val organizationId = runner.organizationId!!
    labels[ORGANIZATION_ID_LABEL] = organizationId
    name += ":$organizationId"
    publishRunStartLabeledMetric(name, labels)

    // By Workspace
    val workspaceId = runner.workspaceId!!
    labels[WORKSPACE_ID_LABEL] = workspaceId
    name += ":$workspaceId"
    publishRunStartLabeledMetric(name, labels)

    // By Runner
    val runnerId = runner.id!!
    labels[RUNNER_ID_LABEL] = runnerId
    name += ":$runnerId"
    publishRunStartLabeledMetric(name, labels)
  }

  private fun getRunningWorkflowsCount(): Map<WorkflowContextData?, Int> {
    val runWorkflows =
        this.workflowService.findWorkflowStatusByLabel("$WORKFLOW_TYPE_LABEL=$WORKFLOW_TYPE_RUN")
    return runWorkflows
        .filter { it.status == RUNNING_STATUS }
        .groupingBy { it.contextData }
        .eachCount()
  }

  internal fun publishRunStartLabeledMetric(name: String, labels: Map<String, String>) {
    val metric =
        PersistentMetric(
            service = SERVICE_NAME,
            name = name,
            value = 0.0,
            incrementBy = 1,
            qualifier = TOTAL_QUALIFIER,
            labels = labels,
            type = PersitentMetricType.COUNTER,
            downSampling = true,
            downSamplingAggregation = DownSamplingAggregationType.MAX,
        )
    eventPublisher.publishEvent(PersistentMetricEvent(this, metric))
  }

  internal fun publishOrganizationWorkspaceAndRunnerRunningLabeledMetricIfAny(
      workflowContext: WorkflowContextData,
      workflowCount: Int,
      name: String,
      labels: MutableMap<String, String>
  ) {
    var existingName = name
    val organizationId = workflowContext.organizationId!!
    if (organizationId != DEFAULT_EMPTY_WORKFLOW_LABEL) {
      labels[ORGANIZATION_ID_LABEL] = organizationId
      existingName += ":$organizationId"
      publishRunningLabeledMetric(workflowCount, existingName, labels)
      val workspaceId = workflowContext.workspaceId!!
      if (workspaceId != DEFAULT_EMPTY_WORKFLOW_LABEL) {
        labels[WORKSPACE_ID_LABEL] = workspaceId
        existingName += ":$workspaceId"
        publishRunningLabeledMetric(workflowCount, existingName, labels)
        val runnerId = workflowContext.runnerId!!
        if (runnerId != DEFAULT_EMPTY_WORKFLOW_LABEL) {
          labels[RUNNER_ID_LABEL] = runnerId
          existingName += ":$runnerId"
          publishRunningLabeledMetric(workflowCount, existingName, labels)
        }
      }
    }
  }

  internal fun publishRunningLabeledMetric(value: Int, name: String, labels: Map<String, String>) {
    val metric =
        PersistentMetric(
            service = SERVICE_NAME,
            name = name,
            value = value.toDouble(),
            qualifier = TOTAL_QUALIFIER,
            labels = labels,
            type = PersitentMetricType.GAUGE,
            downSampling = true,
            downSamplingAggregation = DownSamplingAggregationType.MAX,
        )
    eventPublisher.publishEvent(PersistentMetricEvent(this, metric))
  }
}
