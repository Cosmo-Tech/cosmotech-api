// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.metrics

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.events.CsmEventPublisher
import com.cosmotech.common.events.PersistentMetricEvent
import com.cosmotech.common.events.RunStart
import com.cosmotech.common.metrics.DownSamplingAggregationType
import com.cosmotech.common.metrics.PersistentMetric
import com.cosmotech.common.metrics.PersitentMetricType
import com.cosmotech.runner.domain.Runner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

private const val SERVICE_NAME = "run"
private const val ORGANIZATION_ID_LABEL = "organizationId"
private const val WORKSPACE_ID_LABEL = "workspaceId"
private const val RUNNER_ID_LABEL = "runnerId"
private const val TOTAL_QUALIFIER = "total"
private const val TS_RUN_WORKFLOW_NAME = "run"

@Service
@ConditionalOnProperty(
    name = ["csm.platform.metrics.enabled"], havingValue = "true", matchIfMissing = false)
internal class RunMetrics(
    private val eventPublisher: CsmEventPublisher,
    private val csmPlatformProperties: CsmPlatformProperties,
) {

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
    val organizationId = runner.organizationId
    labels[ORGANIZATION_ID_LABEL] = organizationId
    name += ":$organizationId"
    publishRunStartLabeledMetric(name, labels)

    // By Workspace
    val workspaceId = runner.workspaceId
    labels[WORKSPACE_ID_LABEL] = workspaceId
    name += ":$workspaceId"
    publishRunStartLabeledMetric(name, labels)

    // By Runner
    val runnerId = runner.id
    labels[RUNNER_ID_LABEL] = runnerId
    name += ":$runnerId"
    publishRunStartLabeledMetric(name, labels)
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
}
