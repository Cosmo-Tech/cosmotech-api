// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.metrics

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.PersistentMetricEvent
import com.cosmotech.api.events.ScenarioRunStartedForScenario
import com.cosmotech.api.metrics.DownSamplingAggregationType
import com.cosmotech.api.metrics.PersistentMetric
import com.cosmotech.api.metrics.PersitentMetricType
import com.cosmotech.scenariorun.WORKFLOW_TYPE_LABEL
import com.cosmotech.scenariorun.service.WORKFLOW_TYPE_SCENARIO_RUN
import com.cosmotech.scenariorun.workflow.WorkflowContextData
import com.cosmotech.scenariorun.workflow.WorkflowService
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private const val RUNNING_STATUS = "Running"

private const val SERVICE_NAME = "scenariorun"

private const val ORGANIZATION_ID_LABEL = "organizationId"
private const val WORKSPACE_ID_LABEL = "workspaceId"
private const val SCENARIO_ID_LABEL = "scenarioId"

private const val DEFAULT_EMPTY_WORKFLOW_LABEL = "none"
private const val TOTAL_QUALIFIER = "total"
private const val TS_RUNNING_WORKFLOW_NAME = "running"
private const val TS_RUN_WORKFLOW_NAME = "run"

@Service
internal class ScenarioRunMetrics(
    private val workflowService: WorkflowService,
    private val eventPublisher: CsmEventPublisher,
    private val csmPlatformProperties: CsmPlatformProperties,
) {

  @Scheduled(fixedDelay = 10000)
  fun publishCurrentRunningScenario() {
    if (!csmPlatformProperties.metrics.enabled) return

    val countRunningWorkflows = getRunningWorkflowsCount()
    val labels =
        mutableMapOf(
            "usage" to "licensing",
        )
    // Global
    publishRunningLabeledMetric(
        countRunningWorkflows.values.sum(), TS_RUNNING_WORKFLOW_NAME, labels)

    // By Organization, Workspace, Scenario
    countRunningWorkflows.forEach { (workflowContext, workflowCount) ->
      publishOrganizationWorkspaceAndScenarioRunningLabeledMetricIfAny(
          workflowContext!!, workflowCount, TS_RUNNING_WORKFLOW_NAME, labels)
    }
  }

  @EventListener(ScenarioRunStartedForScenario::class)
  fun onScenarioRunStartedForScenario(event: ScenarioRunStartedForScenario) {
    if (!csmPlatformProperties.metrics.enabled) return

    val labels =
        mutableMapOf(
            "usage" to "licensing",
        )
    var name = TS_RUN_WORKFLOW_NAME

    // Global
    publishRunStartLabeledMetric(name, labels)

    // By Organization
    val organizationId = event.organizationId
    labels[ORGANIZATION_ID_LABEL] = organizationId
    name += ":$organizationId"
    publishRunStartLabeledMetric(name, labels)

    // By Workspace
    val workspaceId = event.workspaceId
    labels[WORKSPACE_ID_LABEL] = workspaceId
    name += ":$workspaceId"
    publishRunStartLabeledMetric(name, labels)

    // By Scenario
    val scenarioId = event.scenarioId
    labels[SCENARIO_ID_LABEL] = scenarioId
    name += ":$scenarioId"
    publishRunStartLabeledMetric(name, labels)
  }

  private fun getRunningWorkflowsCount(): Map<WorkflowContextData?, Int> {
    val runWorkflows =
        this.workflowService.findWorkflowStatusByLabel(
            "$WORKFLOW_TYPE_LABEL=$WORKFLOW_TYPE_SCENARIO_RUN",
            true,
        )
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

  internal fun publishOrganizationWorkspaceAndScenarioRunningLabeledMetricIfAny(
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
        val scenarioId = workflowContext.scenarioId!!
        if (scenarioId != DEFAULT_EMPTY_WORKFLOW_LABEL) {
          labels[SCENARIO_ID_LABEL] = scenarioId
          existingName += ":$scenarioId"
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
