// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.exceptions

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

  @ExceptionHandler(JedisDataException::class)
  fun handleJedisDataException(exception: JedisDataException): ProblemDetail {
    var response = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)

    if (exception.message != null) {
      if (exception.message == "Query timed out") {
        response = ProblemDetail.forStatus(HttpStatus.REQUEST_TIMEOUT)
        response.detail = "Query took to much time, please try to rewrite it"
        response.title = "Query timed out"
      } else {
        response =
            ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.message!!)
      }
    }

    return response
  }
}
