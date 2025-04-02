// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.exceptions

import java.net.URI
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import software.amazon.awssdk.awscore.exception.AwsServiceException

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
internal class AwsExceptionHandling : CsmExceptionHandling() {
  private val httpStatusCodeTypePrefix = "https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/"

  @ExceptionHandler
  fun handleBlobStorageException(exception: AwsServiceException): ProblemDetail {
    val status = HttpStatus.INTERNAL_SERVER_ERROR

    val problemDetail = ProblemDetail.forStatus(status)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + status.value())

    if (exception.message != null) {
      problemDetail.detail = exception.message
    }
    return problemDetail
  }
}
