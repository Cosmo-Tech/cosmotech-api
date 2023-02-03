// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.metrics

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.PersistentMetricEvent
import com.cosmotech.api.metrics.PersistentMetric
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.redislabs.redistimeseries.RedisTimeSeries
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisPool

private const val MILLISECONDS_IN_DAY = 86400000

@Service
class MetricsServiceImpl(
    private val jedisPool: JedisPool,
    private val timeSeries: RedisTimeSeries,
    private val csmPlatformProperties: CsmPlatformProperties,
) : MetricsService {

  private val logger = LoggerFactory.getLogger(MetricsServiceImpl::class.java)

  override fun storeMetric(metric: PersistentMetric) {
    createTimeSeries(metric)
    addMetricToTimeSeries(metric)
  }

  private fun addMetricToTimeSeries(metric: PersistentMetric) {
    logger.debug("METRICS: addMetricToTimeSeries")
    val key = getMetricKey(metric)
    if (metric.incrementBy > 0 && metric.value > 0) {
      throw IllegalArgumentException("Cannot set both incrementBy and value")
    }

    val timestamp = when (metric.incrementBy) {
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

  private fun createTimeSeries(metric: PersistentMetric) {
    logger.debug("METRICS: createTimeSeries")
    jedisPool.resource.use { jedis ->
      val key = getMetricKey(metric)
      logger.debug("Testing Redis TS exist: $key")
      val exist = jedis.exists(key)
      logger.debug("Redis TS exist: ${key}:${exist}")

      val metricRetention = getMetricRetention(metric)

      if (!exist) {
        val metricLabels = getMetricLabels(metric)
        logger.debug("Creating Redis TS: $key with retention: $metricRetention and ${metricLabels.count()} labels")
        timeSeries.create(
            key,
            metricRetention,
            metricLabels,
        )
      } else {
        val timeSeriesRetention = timeSeries.info(key).getProperty("retentionTime")

        if (!timeSeriesRetention.equals(metricRetention)) {
          logger.debug("Redis TS retention changed: $key from $timeSeriesRetention to $metricRetention")
          logger.debug("Redis TS library cannot get current labels so it is not possible to check if labels changed")
          val metricLabels = getMetricLabels(metric)
          timeSeries.alter(key, metricRetention, metricLabels)
        } else {}
      }
    }
  }

  private fun getMetricLabels(metric: PersistentMetric): Map<String, String> {
    val labels = mutableMapOf(
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
      metric.vendor + ":" + metric.service + ":" + metric.name + ":" + metric.qualifier

  private fun getMetricRetention(metric: PersistentMetric) =
      when (metric.retention) {
        0L -> csmPlatformProperties.metrics.retentionDays.toLong() * MILLISECONDS_IN_DAY
        else -> metric.retention
      }

  @EventListener(PersistentMetricEvent::class)
  fun onMetricEvent(event: PersistentMetricEvent) {
    logger.debug("METRICS: onMetricEvent")
    storeMetric(event.metric)
  }
}
