// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.web

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.utils.getCurrentAuthenticatedUserName
import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Logs one structured `api_access` line per request: HTTP method, path, calling user, remote
 * address, status, duration and the current trace/span id, correlated with the request's trace so
 * it can be cross-referenced with the OpenTelemetry span. The same fields are also written to the
 * MDC around the log call, so they show up as top-level fields when structured logging is enabled.
 *
 * The calling user is resolved from the security context (falling back to `"anonymous"` when there
 * is none, e.g. for public endpoints or requests rejected before authentication).
 *
 * Registered as a plain `@Component` with `@Order(HIGHEST_PRECEDENCE + 10)`, which Spring Boot
 * auto-registers as a servlet filter at that order: right after Boot's
 * `ServerHttpObservationFilter` (`HIGHEST_PRECEDENCE + 1`), so [Tracer.currentSpan] reflects the
 * request's span, but before Spring Security's filter chain (registered near
 * `Ordered.LOWEST_PRECEDENCE` by default), so requests rejected or redirected by Security are
 * logged too.
 */
@Component
@Suppress("MagicNumber")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class AuditLogFilter(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val tracer: Tracer,
) : OncePerRequestFilter() {

  private val logger = LoggerFactory.getLogger(AuditLogFilter::class.java)

  override fun doFilterInternal(
      request: HttpServletRequest,
      response: HttpServletResponse,
      filterChain: FilterChain,
  ) {
    val startTime = System.currentTimeMillis()
    try {
      filterChain.doFilter(request, response)
    } finally {
      logAccess(request, response, startTime)
    }
  }

  private fun logAccess(
      request: HttpServletRequest,
      response: HttpServletResponse,
      startTime: Long,
  ) {
    val durationMs = System.currentTimeMillis() - startTime
    val user =
        runCatching { getCurrentAuthenticatedUserName(csmPlatformProperties) }
            .getOrDefault("anonymous")
    val span = tracer.currentSpan()?.context()
    val traceId = span?.traceId() ?: "-"
    val spanId = span?.spanId() ?: "-"
    val method = request.method
    val path = request.requestURI
    val status = response.status
    val remoteAddr = request.remoteAddr

    MDC.put("method", method)
    MDC.put("path", path)
    MDC.put("query", request.queryString ?: "")
    MDC.put("user", user)
    MDC.put("remoteAddr", remoteAddr)
    MDC.put("status", status.toString())
    MDC.put("durationMs", durationMs.toString())
    MDC.put("traceId", traceId)
    MDC.put("spanId", spanId)
    try {
      logger.info(
          "api_access method={} path={} user={} remoteAddr={} status={} durationMs={} traceId={} spanId={}",
          method,
          path,
          user,
          remoteAddr,
          status,
          durationMs,
          traceId,
          spanId,
      )
    } finally {
      MDC.clear()
    }
  }
}
