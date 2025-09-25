// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.metrics

private const val DEFAULT_PROVIDER = "cosmotech"

private const val DEFAULT_QUALIFIER = "data"

private const val DEFAULT_SCOPE = "metric"

private const val DEFAULT_DOWN_SAMPLING_SUFFIX = ":ds"

// key will be ts:scope:vendor:service:name:qualifier
data class PersistentMetric(
    val service: String,
    val name: String,
    val value: Double,
    val incrementBy: Int = 0,
    val labels: Map<String, String> = emptyMap(),
    val qualifier: String = DEFAULT_QUALIFIER,
    val timestamp: Long = System.currentTimeMillis(),
    val vendor: String = DEFAULT_PROVIDER,
    val retention: Long = 0,
    val downSampling: Boolean = false,
    val downSamplingRetention: Long = 0,
    val downSamplingBucketDuration: Long = 0,
    val downSamplingAggregation: DownSamplingAggregationType = DownSamplingAggregationType.AVG,
    val downSamplingSuffix: String = DEFAULT_DOWN_SAMPLING_SUFFIX,
    val type: PersitentMetricType = PersitentMetricType.COUNTER,
    val scope: String = DEFAULT_SCOPE,
)
