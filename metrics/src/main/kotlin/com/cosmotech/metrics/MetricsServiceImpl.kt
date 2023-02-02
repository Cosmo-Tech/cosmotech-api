// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.metrics

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.PersistentMetricEvent
import com.cosmotech.api.metrics.PersistentMetric
import com.redislabs.redistimeseries.RedisTimeSeries
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisPool

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
    if (metric.incrementBy > 0 && metric.value > 0) {
      throw IllegalArgumentException("Cannot set both incrementBy and value")
    }

    when (metric.incrementBy) {
      0 ->
          timeSeries.add(
              getMetricKey(metric),
              metric.timestamp,
              metric.value,
          )
      else ->
          timeSeries.incrBy(
              getMetricKey(metric),
              metric.incrementBy,
              metric.timestamp,
          )
    }
  }

  private fun createTimeSeries(metric: PersistentMetric) {
    jedisPool.resource.use { jedis ->
      val key = getMetricKey(metric)
      logger.debug("Testing Redis TS exist: $key")
      val exist = jedis.exists(key)
      logger.debug("Redis TS exist: ${key}:${exist}")
      if (!exist) {
        val retention = getMetricRetention(metric)
        logger.debug("Creating Redis TS: $key with retention: $retention")
        timeSeries.create(
            key,
            retention,
            metric.tags,
        )
      }
    }
  }

  private fun getMetricKey(metric: PersistentMetric) =
      metric.vendor + ":" + metric.service + ":" + metric.name + ":" + metric.qualifier

  private fun getMetricRetention(metric: PersistentMetric) =
      when (metric.retention) {
        0L -> csmPlatformProperties.metrics.retentionDays.toLong()
        else -> metric.retention
      }

  @EventListener(PersistentMetricEvent::class)
  fun onMetricEvent(event: PersistentMetricEvent) {
    storeMetric(event.metric)
  }
}
