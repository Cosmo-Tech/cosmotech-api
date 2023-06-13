// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import io.micrometer.core.aop.TimedAspect
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MetricsConfig {

  @Bean
  fun timedAspect(registry: MeterRegistry): TimedAspect {
    return TimedAspect(registry)
  }
}
