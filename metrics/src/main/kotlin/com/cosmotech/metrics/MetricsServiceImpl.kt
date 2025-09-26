// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.metrics

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.events.PersistentMetricEvent
import com.cosmotech.common.metrics.PersistentMetric
import com.cosmotech.common.metrics.toRedisAggregation
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.timeseries.DuplicatePolicy
import redis.clients.jedis.timeseries.TSAlterParams
import redis.clients.jedis.timeseries.TSCreateParams

private const val MILLISECONDS_IN_DAY = 86400000
private const val KEY_PREFIX = "ts2"

@Service
class MetricsServiceImpl(
    private val unifiedJedis: UnifiedJedis,
    private val csmPlatformProperties: CsmPlatformProperties,
) : MetricsService {

  private val logger = LoggerFactory.getLogger(MetricsServiceImpl::class.java)

  override fun storeMetric(metric: PersistentMetric) {
    createOrAlterTimeSeries(metric)
    addMetricToTimeSeries(metric)
  }

  private fun addMetricToTimeSeries(metric: PersistentMetric) {
    val key = getMetricKey(metric)
    require(!(metric.incrementBy > 0 && metric.value > 0)) {
      "Cannot set both incrementBy and value"
    }
    val timestamp =
        when (metric.incrementBy) {
          0 -> unifiedJedis.tsAdd(key, metric.value)
          else -> unifiedJedis.tsIncrBy(key, metric.incrementBy.toDouble())
        }

    logger.debug("METRICS: addMetricToTimeSeries done for $key at $timestamp")
  }

  @Suppress("EmptyElseBlock")
  private fun createOrAlterTimeSeries(metric: PersistentMetric) {
    val key = getMetricKey(metric)
    logger.debug("Testing Redis TS exist: $key")
    val exist = unifiedJedis.exists(key)
    logger.debug("Redis TS exist: $key:$exist")

    val metricRetention = getMetricRetention(metric)

    val commonLabels = getCommonLabels(metric)
    if (!exist) {
      val metricLabels = getMetricLabels(commonLabels)
      logger.debug(
          "Creating Redis TS: $key with retention: $metricRetention and ${metricLabels.count()} labels")
      unifiedJedis.tsCreate(
          key,
          TSCreateParams()
              .retention(metricRetention)
              .labels(metricLabels)
              .duplicatePolicy(DuplicatePolicy.MAX))
      if (metric.downSampling || csmPlatformProperties.metrics.downSamplingDefaultEnabled) {
        val downSamplingKey = getDownSamplingKey(metric)
        val downSamplingMetricLabels = getDownSamplingMetricLabels(commonLabels)
        val downSamplingRetention = getDownSamplingRetention(metric)
        val downSamplingBucketDuration = getDownSamplingBucketDuration(metric)
        logger.debug(
            "Creating Redis DownSampling TS: $downSamplingKey with retention: $metricRetention " +
                "and ${downSamplingMetricLabels.count()} labels")
        unifiedJedis.tsCreate(
            downSamplingKey,
            TSCreateParams()
                .retention(downSamplingRetention)
                .labels(downSamplingMetricLabels)
                .duplicatePolicy(DuplicatePolicy.MAX))
        logger.debug(
            "Creating Redis DownSampling TS rule: from $key to $downSamplingKey, " +
                "aggregation: ${metric.downSamplingAggregation.value}, bucketDuration: $downSamplingBucketDuration")
        unifiedJedis.tsCreateRule(
            key,
            downSamplingKey,
            metric.downSamplingAggregation.toRedisAggregation(),
            downSamplingBucketDuration,
            0)
      }
    } else {
      val timeSeriesRetention = unifiedJedis.tsInfo(key).getProperty("retentionTime")

      if (timeSeriesRetention != metricRetention) {
        logger.debug(
            "Redis TS retention changed: $key from $timeSeriesRetention to $metricRetention")
        logger.debug(
            "Redis TS library cannot get current labels so it is not possible to check if labels changed")
        val metricLabels = getMetricLabels(commonLabels)
        unifiedJedis.tsAlter(
            key,
            TSAlterParams()
                .retention(metricRetention)
                .labels(metricLabels)
                .duplicatePolicy(DuplicatePolicy.MAX))
      }
    }
  }

  private fun getDownSamplingBucketDuration(metric: PersistentMetric): Long =
      when (metric.downSamplingBucketDuration) {
        0L -> csmPlatformProperties.metrics.downSamplingBucketDurationMs.toLong()
        else -> metric.downSamplingBucketDuration
      }

  private fun getDownSamplingKey(metric: PersistentMetric): String =
      "${getMetricKey(metric)}${metric.downSamplingSuffix}"

  private fun getDownSamplingMetricLabels(metricLabels: Map<String, String>): Map<String, String> {
    val labels =
        mutableMapOf(
            "downsampling" to "true",
        )

    labels.putAll(metricLabels)
    return labels.toMap()
  }

  private fun getMetricLabels(metricLabels: Map<String, String>): Map<String, String> {
    val labels =
        mutableMapOf(
            "downsampling" to "false",
        )

    labels.putAll(metricLabels)
    return labels.toMap()
  }

  private fun getCommonLabels(metric: PersistentMetric): Map<String, String> {
    val labels =
        mutableMapOf(
            "scope" to metric.scope,
            "service" to metric.service,
            "name" to metric.name,
            "qualifier" to metric.qualifier,
            "vendor" to metric.vendor,
            "type" to metric.type.name,
        )

    labels.putAll(metric.labels)
    return labels.toMap()
  }

  private fun getMetricKey(metric: PersistentMetric) =
      "$KEY_PREFIX:${metric.scope}:${metric.vendor}:${metric.service}:${metric.name}:${metric.qualifier}"

  private fun getMetricRetention(metric: PersistentMetric) =
      when (metric.retention) {
        0L -> csmPlatformProperties.metrics.retentionDays.toLong() * MILLISECONDS_IN_DAY
        else -> metric.retention
      }

  private fun getDownSamplingRetention(metric: PersistentMetric) =
      when (metric.downSamplingRetention) {
        0L -> csmPlatformProperties.metrics.downSamplingRetentionDays.toLong() * MILLISECONDS_IN_DAY
        else -> metric.downSamplingRetention
      }

  @EventListener(PersistentMetricEvent::class)
  fun onMetricEvent(event: PersistentMetricEvent) {
    storeMetric(event.metric)
  }
}
