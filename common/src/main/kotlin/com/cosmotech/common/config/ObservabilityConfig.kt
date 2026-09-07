// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.cosmotech.common.utils.getCurrentAuthenticatedUserName
import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention
import org.springframework.http.server.observation.ServerRequestObservationContext

/**
 * OpenTelemetry / Micrometer wiring shared by every Cosmo Tech API service.
 *
 * Traces and logs are produced through Micrometer Observation and exported over OTLP (see the
 * `management.opentelemetry.*` and `management.otlp.*` properties). Nothing is exported until
 * `OTEL_EXPORT_ENABLED=true` is set, so the defaults are safe for local runs. Metrics keep using
 * the existing Prometheus registry and are not affected by this configuration.
 */
@Configuration(proxyBeanMethods = false)
class ObservabilityConfig(private val csmPlatformProperties: CsmPlatformProperties) {

  @Bean
  fun apiServerRequestObservationConvention(): ApiServerRequestObservationConvention =
      ApiServerRequestObservationConvention(csmPlatformProperties)

  /**
   * Routes application logs to the OpenTelemetry log pipeline (OTLP), keeping the existing
   * `logback.xml` console appender untouched. Log records are automatically correlated with the
   * current trace/span.
   */
  @Bean
  fun openTelemetryAppenderInitializer(openTelemetry: OpenTelemetry) =
      OpenTelemetryLogbackAppenderInitializer(openTelemetry)
}

/**
 * Enriches the `http.server.requests` observation (and therefore the OpenTelemetry server span)
 * with audit-oriented attributes. Only low cardinality values end up as metric tags; everything
 * user/request specific is exposed as high cardinality data, attached to spans only.
 */
class ApiServerRequestObservationConvention(
    private val csmPlatformProperties: CsmPlatformProperties
) : DefaultServerRequestObservationConvention() {

  override fun getName() = "http.server.requests"

  override fun getHighCardinalityKeyValues(context: ServerRequestObservationContext): KeyValues {
    val request = context.carrier
    return super.getHighCardinalityKeyValues(context)
        .and(
            KeyValue.of(ENDUSER_ID, currentUserOrAnonymous()),
            KeyValue.of(URL_QUERY, request?.queryString ?: ""),
            KeyValue.of(CLIENT_ADDRESS, request?.remoteAddr ?: ""),
            KeyValue.of(USER_AGENT, request?.getHeader("User-Agent") ?: ""),
        )
  }

  private fun currentUserOrAnonymous(): String =
      runCatching { getCurrentAuthenticatedUserName(csmPlatformProperties) }
          .getOrDefault("anonymous")

  private companion object {
    const val ENDUSER_ID = "enduser.id"
    const val URL_QUERY = "url.query"
    const val CLIENT_ADDRESS = "client.address"
    const val USER_AGENT = "user_agent.original"
  }
}

/**
 * Installs the OpenTelemetry Logback appender programmatically, which avoids having to provide a
 * full `logback-spring.xml` and therefore keeps `logback.xml`'s console appender untouched.
 */
class OpenTelemetryLogbackAppenderInitializer(private val openTelemetry: OpenTelemetry) :
    InitializingBean {

  override fun afterPropertiesSet() {
    val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
    val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
    if (rootLogger.getAppender(APPENDER_NAME) != null) {
      OpenTelemetryAppender.install(openTelemetry)
      return
    }
    val appender =
        OpenTelemetryAppender().apply {
          name = APPENDER_NAME
          context = loggerContext
          setCaptureExperimentalAttributes(true)
          setCaptureCodeAttributes(true)
          setCaptureMarkerAttribute(true)
          setCaptureKeyValuePairAttributes(true)
          setCaptureLoggerContext(true)
          setCaptureMdcAttributes("*")
          start()
        }
    OpenTelemetryAppender.install(openTelemetry)
    rootLogger.addAppender(appender)
  }

  private companion object {
    const val APPENDER_NAME = "OTEL"
  }
}
