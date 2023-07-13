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


@Service
internal class ScenarioRunMetrics(
    private val workflowService: WorkflowService,
    private val eventPublisher: CsmEventPublisher,
    private val csmPlatformProperties: CsmPlatformProperties,
) {
  private fun getRunningWorkflowsCount(): Map<WorkflowContextData?, Int> {
    val runWorkflows = this.workflowService.findWorkflowStatusByLabel(
        "$WORKFLOW_TYPE_LABEL=$WORKFLOW_TYPE_SCENARIO_RUN",
        true,
    )
    return runWorkflows.filter { it.status == RUNNING_STATUS }.groupingBy { it.contextData }.eachCount()
  }

  private fun publishRunningLabeledMetric(value: Int, name: String, labels: Map<String, String>) {
    val orgaMetric = PersistentMetric(
        service = SERVICE_NAME,
        name = name,
        value = value.toDouble(),
        qualifier = "total",
        labels = labels,
        type = PersitentMetricType.GAUGE,
        downSampling = true,
        downSamplingAggregation = DownSamplingAggregationType.SUM,
    )
    eventPublisher.publishEvent(PersistentMetricEvent(this, orgaMetric))
  }

  @Scheduled(fixedDelay = 10000)
  fun publishCurrentRunningScenario() {
    if (!csmPlatformProperties.metrics.enabled) return
    val countRunningWorkflows = getRunningWorkflowsCount()
    var totalRunningWorklowsCount = 0
    countRunningWorkflows.forEach { (workflowContext, workflowCount) ->
      totalRunningWorklowsCount += workflowCount
      if (workflowContext!!.organizationId != "none") {
        publishRunningLabeledMetric(
            workflowCount,
            "running:" + workflowContext.organizationId,
            mapOf(
                "usage" to "licensing",
                "organizationId" to workflowContext.organizationId!!,
            ))
        if (workflowContext.workspaceId != "none") {
          publishRunningLabeledMetric(
              workflowCount,
              "running:" + workflowContext.organizationId + ":" + workflowContext.workspaceId,
              mapOf(
                  "usage" to "licensing",
                  "organizationId" to workflowContext.organizationId,
                  "workspaceId" to workflowContext.workspaceId!!,
              ))
        }
      }
    }
    val metric = PersistentMetric(
        service = SERVICE_NAME,
        name = "running",
        value = totalRunningWorklowsCount.toDouble(),
        qualifier = "total",
        labels = mapOf(
            "usage" to "licensing",
        ),
        type = PersitentMetricType.GAUGE,
        downSampling = true,
        downSamplingAggregation = DownSamplingAggregationType.MAX,
    )
    eventPublisher.publishEvent(PersistentMetricEvent(this, metric))
  }

  @EventListener(ScenarioRunStartedForScenario::class)
  @Suppress("UnusedPrivateMember")
  fun onScenarioRunStartedForScenario(event: ScenarioRunStartedForScenario) {
    if (!csmPlatformProperties.metrics.enabled) return
    val metric = PersistentMetric(
        service = SERVICE_NAME,
        name = "run",
        value = 0.0,
        incrementBy = 1,
        qualifier = "total",
        labels = mapOf(
            "usage" to "licensing",
        ),
        type = PersitentMetricType.COUNTER,
        downSampling = true,
        downSamplingAggregation = DownSamplingAggregationType.MAX,
    )
    eventPublisher.publishEvent(PersistentMetricEvent(this, metric))
    val organizationMetric = PersistentMetric(
        service = SERVICE_NAME,
        name = "run:" + event.organizationId,
        value = 0.0,
        incrementBy = 1,
        qualifier = "total",
        labels = mapOf(
            "usage" to "licensing",
            "organizationId" to event.organizationId,
        ),
        type = PersitentMetricType.COUNTER,
        downSampling = true,
        downSamplingAggregation = DownSamplingAggregationType.MAX,
    )
    eventPublisher.publishEvent(PersistentMetricEvent(this, organizationMetric))
    val workspaceMetric = PersistentMetric(
        service = SERVICE_NAME,
        name = "run:" + event.organizationId + ":" + event.workspaceId,
        value = 0.0,
        incrementBy = 1,
        qualifier = "total",
        labels = mapOf(
            "usage" to "licensing",
            "organizationId" to event.organizationId,
            "workspaceId" to event.workspaceId,
        ),
        type = PersitentMetricType.COUNTER,
        downSampling = true,
        downSamplingAggregation = DownSamplingAggregationType.MAX,
    )
    eventPublisher.publishEvent(PersistentMetricEvent(this, workspaceMetric))
  }
}
