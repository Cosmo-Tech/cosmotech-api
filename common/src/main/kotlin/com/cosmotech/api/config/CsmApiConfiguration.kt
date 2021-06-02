// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.cosmotech.api.config.CsmPlatformProperties.Vendor.AZURE
import com.cosmotech.api.utils.yamlObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.apache.commons.logging.Log
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.context.annotation.AdviceMode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.http.MediaType
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.EnableWebMvc


@Configuration
@ConfigurationPropertiesScan(basePackages = ["com.cosmotech"])
@EnableAsync(mode = AdviceMode.PROXY, proxyTargetClass = true)
class CsmApiConfiguration {

  @Bean(name = ["csm-in-process-event-executor"])
  fun inProcessEventHandlerExecutor(): Executor =
      // TODO A better strategy could be with a limited core pool size off an unbounded queue ?
      Executors.newCachedThreadPool(
          BasicThreadFactory.Builder().namingPattern("csm-event-handler-%d").build())

  @Bean fun yamlHttpMessageConverter(): YamlMessageConverter = YamlMessageConverter()
}

@Order(Ordered.HIGHEST_PRECEDENCE)
class CsmPlatformEnvironmentPostProcessor(private val log: Log) : EnvironmentPostProcessor {

  override fun postProcessEnvironment(
      environment: ConfigurableEnvironment,
      application: SpringApplication
  ) {
    val platform = (System.getenv("CSM_PLATFORM_VENDOR") ?: AZURE.toString()).lowercase()
    if (platform.isNotBlank()) {
      // Automatically add the 'azure' Spring Profile before the application context is refreshed
      if (environment.activeProfiles.none { platform.equals(it, ignoreCase = true) }) {
        log.debug("Adding '$platform' as an active profile")
        environment.addActiveProfile(platform)
      } else {
        log.debug("'$platform' is already an active profile")
      }
    }
  }
}

/**
 * Implementation of {@link org.springframework.http.converter.HttpMessageConverter
 * HttpMessageConverter} that can read and write YAML using <a
 * href="https://github.com/FasterXML/jackson-dataformats-text/tree/master/yaml"> Jackson extension
 * component for reading and writing YAML encoded data</a>.
 *
 * By default, this converter supports {@code application/yaml}, {@code application/yml}, {@code
 * text/yaml}, {@code text/yml}, {@code application/<*>+yaml}, and {@code application/<*>+yml} with
 * {@code UTF-8} character set.
 *
 * This can be overridden by setting the {@link #setSupportedMediaTypes supportedMediaTypes}
 * property.
 */
class YamlMessageConverter(objectMapper: ObjectMapper) :
    AbstractJackson2HttpMessageConverter(
        objectMapper,
        MediaType("application", "yaml", StandardCharsets.UTF_8),
        MediaType("text", "yaml", StandardCharsets.UTF_8),
        MediaType("application", "*+yaml", StandardCharsets.UTF_8),
        MediaType("application", "yml", StandardCharsets.UTF_8),
        MediaType("text", "yml", StandardCharsets.UTF_8),
        MediaType("application", "*+yml", StandardCharsets.UTF_8)) {

  constructor() : this(yamlObjectMapper())

  override fun setObjectMapper(objectMapper: ObjectMapper) {
    if (objectMapper.factory !is YAMLFactory) {
      throw IllegalArgumentException("ObjectMapper must be configured with YAMLFactory")
    }
    super.setObjectMapper(objectMapper)
  }
}

@Configuration
@EnableWebMvc
class WebConfig: WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
    }
}
