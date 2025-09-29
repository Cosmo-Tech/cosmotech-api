// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.metrics

import redis.clients.jedis.timeseries.AggregationType

enum class DownSamplingAggregationType(val value: String) {
  AVG("avg"),
  SUM("sum"),
  MIN("min"),
  MAX("max"),
  RANGE("range"),
  COUNT("count"),
  FIRST("first"),
  LAST("last"),
  STDP("std.p"),
  STDS("std.s)"),
  VARP("var.p"),
  VARS("var.s"),
}

fun DownSamplingAggregationType.toRedisAggregation(): AggregationType {
  return when (this) {
    DownSamplingAggregationType.AVG -> AggregationType.AVG
    DownSamplingAggregationType.SUM -> AggregationType.SUM
    DownSamplingAggregationType.MIN -> AggregationType.MIN
    DownSamplingAggregationType.MAX -> AggregationType.MAX
    DownSamplingAggregationType.RANGE -> AggregationType.RANGE
    DownSamplingAggregationType.COUNT -> AggregationType.COUNT
    DownSamplingAggregationType.FIRST -> AggregationType.FIRST
    DownSamplingAggregationType.LAST -> AggregationType.LAST
    DownSamplingAggregationType.STDP -> AggregationType.STD_P
    DownSamplingAggregationType.STDS -> AggregationType.STD_S
    DownSamplingAggregationType.VARP -> AggregationType.VAR_P
    DownSamplingAggregationType.VARS -> AggregationType.VAR_S
  }
}
