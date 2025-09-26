// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.events

import com.cosmotech.common.metrics.PersistentMetric

class PersistentMetricEvent(
    publisher: Any,
    val metric: PersistentMetric,
) : CsmEvent(publisher)
