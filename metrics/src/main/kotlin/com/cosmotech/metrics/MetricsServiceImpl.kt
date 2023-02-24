// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.metrics

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.PersistentMetricEvent
import com.cosmotech.api.metrics.PersistentMetric
import com.cosmotech.api.metrics.toRedisAggregation
import com.redislabs.redistimeseries.RedisTimeSeries
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisPool

private const val MILLISECONDS_IN_DAY = 86400000
private const val KEY_PREFIX = "ts"

@Service
class MetricsServiceImpl(
    private val jedisPool: JedisPool,
    private val timeSeries: RedisTimeSeries,
    private val csmPlatformProperties: CsmPlatformProperties,
) : MetricsService {

  private val logger = LoggerFactory.getLogger(MetricsServiceImpl::class.java)

  override fun storeMetric(metric: PersistentMetric) {
    createOrAlterTimeSeries(metric)
    addMetricToTimeSeries(metric)
  }

  private fun addMetricToTimeSeries(metric: PersistentMetric) {
    val key = getMetricKey(metric)
    if (metric.incrementBy > 0 && metric.value > 0) {
      throw IllegalArgumentException("Cannot set both incrementBy and value")
    }

    val timestamp =
        when (metric.incrementBy) {
          0 ->
              timeSeries.add(
                  key,
                  metric.timestamp,
                  metric.value,
              )
          else ->
              timeSeries.incrBy(
                  key,
                  metric.incrementBy,
                  metric.timestamp,
              )
        }

    logger.debug("METRICS: addMetricToTimeSeries done for $key at $timestamp")
  }

  @Suppress("EmptyElseBlock")
  private fun createOrAlterTimeSeries(metric: PersistentMetric) {
    jedisPool.resource.use { jedis ->
      val key = getMetricKey(metric)
      logger.debug("Testing Redis TS exist: $key")
      val exist = jedis.exists(key)
      logger.debug("Redis TS exist: ${key}:${exist}")

      val metricRetention = getMetricRetention(metric)

      val commonLabels = getCommonLabels(metric)
      if (!exist) {
        val metricLabels = getMetricLabels(commonLabels)
        logger.debug(
            "Creating Redis TS: $key with retention: $metricRetention and ${metricLabels.count()} labels")
        timeSeries.create(
            key,
            metricRetention,
            metricLabels,
        )
        if (metric.downSampling || csmPlatformProperties.metrics.downSamplingDefaultEnabled) {
          val downSamplingKey = getDownSamplingKey(metric)
          val downSamplingMetricLabels = getDownSamplingMetricLabels(commonLabels)
          val downSamplingRetention = getDownSamplingRetention(metric)
          val downSamplingBucketDuration = getDownSamplingBucketDuration(metric)
          logger.debug(
              "Creating Redis DownSampling TS: $downSamplingKey with retention: $metricRetention " +
                  "and ${downSamplingMetricLabels.count()} labels")
          timeSeries.create(
              downSamplingKey,
              downSamplingRetention,
              downSamplingMetricLabels,
          )
          logger.debug(
              "Creating Redis DownSampling TS rule: from $key to $downSamplingKey, " +
                  "aggregation: ${metric.downSamplingAggregation.value}, bucketDuration: $downSamplingBucketDuration")
          timeSeries.createRule(
              key,
              metric.downSamplingAggregation.toRedisAggregation(),
              downSamplingBucketDuration,
              downSamplingKey,
          )
        } else {}
      } else {
        val timeSeriesRetention = timeSeries.info(key).getProperty("retentionTime")

        if (!timeSeriesRetention.equals(metricRetention)) {
          logger.debug(
              "Redis TS retention changed: $key from $timeSeriesRetention to $metricRetention")
          logger.debug(
              "Redis TS library cannot get current labels so it is not possible to check if labels changed")
          val metricLabels = getMetricLabels(commonLabels)
          timeSeries.alter(key, metricRetention, metricLabels)
        } else {}
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
