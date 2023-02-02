// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.metrics

import com.cosmotech.api.metrics.PersistentMetric

interface MetricsService {
  fun storeMetric(metric: PersistentMetric)
}
