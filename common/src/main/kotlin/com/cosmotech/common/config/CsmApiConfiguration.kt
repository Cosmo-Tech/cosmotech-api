// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.config

import com.cosmotech.common.utils.jsonObjectMapper
import com.cosmotech.common.utils.yamlObjectMapper
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.logging.DeferredLog
import org.springframework.context.annotation.AdviceMode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.converter.yaml.JacksonYamlHttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@ConfigurationPropertiesScan(basePackages = ["com.cosmotech"])
@EnableAsync(mode = AdviceMode.PROXY, proxyTargetClass = true)
@EnableAspectJAutoProxy
open class CsmApiConfiguration {

  @Bean(name = ["csm-in-process-event-executor"])
  open fun inProcessEventHandlerExecutor(): Executor =
      Executors.newCachedThreadPool(
          BasicThreadFactory.builder().namingPattern("csm-event-handler-%d").build()
      )

  @Bean
  open fun yamlHttpMessageConverter(): JacksonYamlHttpMessageConverter {
    val jacksonYamlHttpMessageConverter = JacksonYamlHttpMessageConverter(yamlObjectMapper())
    jacksonYamlHttpMessageConverter.supportedMediaTypes =
        listOf(
            MediaType("application", "yaml", StandardCharsets.UTF_8),
            MediaType("text", "yaml", StandardCharsets.UTF_8),
            MediaType("application", "*+yaml", StandardCharsets.UTF_8),
            MediaType("application", "yml", StandardCharsets.UTF_8),
            MediaType("text", "yml", StandardCharsets.UTF_8),
            MediaType("application", "*+yml", StandardCharsets.UTF_8),
        )
    return jacksonYamlHttpMessageConverter
  }

  @Bean
  open fun jsonHttpMessageConverter(): JacksonJsonHttpMessageConverter {
    val jacksonJsonHttpMessageConverter = JacksonJsonHttpMessageConverter(jsonObjectMapper())
    return jacksonJsonHttpMessageConverter
  }
}

@Order(Ordered.HIGHEST_PRECEDENCE)
open class CsmPlatformEnvironmentPostProcessor : EnvironmentPostProcessor {
  private val log = DeferredLog()

  override fun postProcessEnvironment(
      environment: ConfigurableEnvironment,
      application: SpringApplication,
  ) {
    addSpringProfile(environment)
  }

  private fun addSpringProfile(environment: ConfigurableEnvironment, profile: String = "keycloak") {
    if (profile.isNotBlank()) {
      if (environment.activeProfiles.none { profile.equals(it, ignoreCase = true) }) {
        log.debug("Adding '$profile' as an active profile")
        environment.addActiveProfile(profile)
      } else {
        log.debug("'$profile' is already an active profile")
      }
    }
  }
}

@Configuration
internal open class WebConfig : WebMvcConfigurer {

  override fun addCorsMappings(registry: CorsRegistry) {
    registry.addMapping("/**")
  }
}
