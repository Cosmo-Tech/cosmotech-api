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
import com.cosmotech.scenariorun.azure.WORKFLOW_TYPE_SCENARIO_RUN
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
  private fun getCurrentRunningScenario(): Double {
    val runWorkflows =
        this.workflowService.findWorkflowStatusByLabel(
            "$WORKFLOW_TYPE_LABEL=$WORKFLOW_TYPE_SCENARIO_RUN",
            true,
        )
    return runWorkflows.filter { it.status == RUNNING_STATUS }.size.toDouble()
  }

  @Scheduled(fixedDelay = 10000)
  fun publishCurrentRunningScenario() {
    if (!csmPlatformProperties.metrics.enabled) return
    val currentRunningScenario = getCurrentRunningScenario()
    val metric =
        PersistentMetric(
            service = SERVICE_NAME,
            name = "running",
            value = currentRunningScenario.toDouble(),
            qualifier = "total",
            labels =
                mapOf(
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
    val metric =
        PersistentMetric(
            service = SERVICE_NAME,
            name = "run",
            value = 0.0,
            incrementBy = 1,
            qualifier = "total",
            labels =
                mapOf(
                    "usage" to "licensing",
                ),
            type = PersitentMetricType.COUNTER,
            downSampling = true,
            downSamplingAggregation = DownSamplingAggregationType.MAX,
        )
    eventPublisher.publishEvent(PersistentMetricEvent(this, metric))
  }
}
