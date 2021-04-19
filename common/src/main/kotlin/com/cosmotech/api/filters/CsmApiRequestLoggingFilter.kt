// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.filters

import javax.annotation.PostConstruct
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.CommonsRequestLoggingFilter

private const val DEFAULT_MAX_PAYLOAD_LENGTH = 50

@Component
class CsmApiRequestLoggingFilter : CommonsRequestLoggingFilter() {

  @Value("\${api.request-logging.client-info:true}")
  private lateinit var requestLoggingClientInfo: String

  @Value("\${api.request-logging.query-string:true}")
  private lateinit var requestLoggingQueryString: String

  @Value("\${api.request-logging.max-payload-length:$DEFAULT_MAX_PAYLOAD_LENGTH}")
  private lateinit var requestLoggingMaxPayloadLength: String

  private val logger = LoggerFactory.getLogger(CsmApiRequestLoggingFilter::class.java)

  @PostConstruct
  fun init() {
    isIncludeClientInfo = requestLoggingClientInfo.toBoolean()
    isIncludeQueryString = requestLoggingQueryString.toBoolean()
    isIncludeHeaders = logger.isDebugEnabled
    isIncludePayload = logger.isTraceEnabled
    maxPayloadLength = requestLoggingMaxPayloadLength.toInt()
  }

  override fun shouldLog(request: HttpServletRequest) = true
}
