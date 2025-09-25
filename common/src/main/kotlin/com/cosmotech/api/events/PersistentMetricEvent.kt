// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

import com.cosmotech.api.metrics.PersistentMetric

class PersistentMetricEvent(
    publisher: Any,
    val metric: PersistentMetric,
) : CsmEvent(publisher)
