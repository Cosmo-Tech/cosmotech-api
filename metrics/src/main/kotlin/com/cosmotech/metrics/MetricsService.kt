// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.metrics

import com.cosmotech.api.metrics.PersistentMetric

interface MetricsService {
  /**
   * Store a metric in the persistent database.
   *
   * @param metric The metric to store.
   */
  fun storeMetric(metric: PersistentMetric)
}
