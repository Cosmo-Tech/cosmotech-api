// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.exceptions

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.NativeWebRequest
import org.zalando.problem.Problem
import org.zalando.problem.Status
import org.zalando.problem.spring.web.advice.ProblemHandling
import redis.clients.jedis.exceptions.JedisDataException

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class TwingraphExceptionHandling : ProblemHandling {

  override fun isCausalChainsEnabled() = true

  @ExceptionHandler(JedisDataException::class)
  fun handleJedisDataException(
      exception: JedisDataException,
      request: NativeWebRequest
  ): ResponseEntity<Problem>? {

    var response = create(exception, request)
    if (exception.message == "Query timed out") {
      response =
          ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
              .body(
                  Problem.builder()
                      .withStatus(Status.REQUEST_TIMEOUT)
                      .withDetail("Query took to much time, please try to rewrite it")
                      .withTitle("Query timed out")
                      .build())
    }

    return response
  }
}
