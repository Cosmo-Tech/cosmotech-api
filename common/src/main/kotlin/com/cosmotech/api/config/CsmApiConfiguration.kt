// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.AdviceMode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

@Configuration
@ConfigurationPropertiesScan(basePackages = ["com.cosmotech"])
@EnableAsync(mode = AdviceMode.PROXY, proxyTargetClass = true)
class CsmApiConfiguration {

  @Bean(name = ["csm-in-process-event-executor"])
  fun taskExecutor(): Executor =
      // TODO A better strategy could be with a limited core pool size off an unbounded queue ?
      Executors.newCachedThreadPool(
          BasicThreadFactory.Builder().namingPattern("csm-event-handler-%d").build())
}
