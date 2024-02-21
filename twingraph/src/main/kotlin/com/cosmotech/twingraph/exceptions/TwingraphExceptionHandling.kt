// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.exceptions

import java.net.URI
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import redis.clients.jedis.exceptions.JedisDataException

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class TwingraphExceptionHandling {

  private val httpStatusCodeTypePrefix = "https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/"

  @ExceptionHandler(JedisDataException::class)
  fun handleJedisDataException(exception: JedisDataException): ProblemDetail {
    var response = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    val internalServerErrorStatus = HttpStatus.INTERNAL_SERVER_ERROR
    response.type = URI.create(httpStatusCodeTypePrefix + internalServerErrorStatus.value())

    if (exception.message != null) {
      if (exception.message == "Query timed out") {
        val requestTimeoutStatus = HttpStatus.REQUEST_TIMEOUT
        response = ProblemDetail.forStatus(requestTimeoutStatus)
        response.detail = "Query took to much time, please try to rewrite it"
        response.title = "Query timed out"
        response.type = URI.create(httpStatusCodeTypePrefix + requestTimeoutStatus.value())
      } else {
        response.detail = exception.message
      }
    }

    return response
  }
}
