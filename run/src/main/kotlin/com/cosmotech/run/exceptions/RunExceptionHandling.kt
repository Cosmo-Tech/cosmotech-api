// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.exceptions

import java.net.URI
import java.sql.SQLException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RunExceptionHandling {

  private val httpStatusCodeTypePrefix = "https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/"

  @ExceptionHandler(SQLException::class)
  fun handleSQLException(exception: SQLException): ProblemDetail {
    var response = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)
    val badRequestError = HttpStatus.BAD_REQUEST
    response.type = URI.create(httpStatusCodeTypePrefix + badRequestError.value())
    response.title = "Bad SQL Request"
    response.detail = exception.message

    return response
  }
}
