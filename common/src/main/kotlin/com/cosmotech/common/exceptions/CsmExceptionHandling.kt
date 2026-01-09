// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.exceptions

import io.awspring.cloud.s3.S3Exception
import io.kubernetes.client.custom.QuantityFormatException
import jakarta.validation.ConstraintViolationException
import java.net.URI
import org.apache.commons.lang3.NotImplementedException
import org.hibernate.validator.internal.engine.ConstraintViolationImpl
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.util.BindErrorUtils
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

@Suppress("TooManyFunctions")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
open class CsmExceptionHandling : ResponseEntityExceptionHandler() {

  private val httpStatusCodeTypePrefix = "https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/"

  override fun handleHttpMessageNotReadable(
      exception: HttpMessageNotReadableException,
      headers: HttpHeaders,
      status: HttpStatusCode,
      request: WebRequest,
  ): ResponseEntity<Any>? {
    val badRequestStatus = HttpStatus.BAD_REQUEST
    val problemDetail = ProblemDetail.forStatus(badRequestStatus)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + badRequestStatus.value())

    if (exception.message != null) {
      problemDetail.detail = exception.message
    }
    return super.handleExceptionInternal(exception, problemDetail, headers, status, request)
  }

  override fun handleMethodArgumentNotValid(
      exception: MethodArgumentNotValidException,
      headers: HttpHeaders,
      status: HttpStatusCode,
      request: WebRequest,
  ): ResponseEntity<Any>? {
    val badRequestStatus = HttpStatus.BAD_REQUEST
    val problemDetail = ProblemDetail.forStatus(badRequestStatus)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + badRequestStatus.value())
    val globalErrors = BindErrorUtils.resolveAndJoin(exception.globalErrors)
    val fieldErrors = BindErrorUtils.resolveAndJoin(exception.fieldErrors)
    if (globalErrors.isBlank() && fieldErrors.isBlank()) {
      problemDetail.detail = exception.message
    } else {
      problemDetail.detail = "$globalErrors $fieldErrors".trim()
    }

    return super.handleExceptionInternal(exception, problemDetail, headers, status, request)
  }

  @ExceptionHandler
  fun handleIllegalArgumentException(exception: IllegalArgumentException): ProblemDetail {
    val badRequestStatus = HttpStatus.BAD_REQUEST
    val problemDetail = ProblemDetail.forStatus(badRequestStatus)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + badRequestStatus.value())

    if (exception.message != null) {
      problemDetail.detail = exception.message
    }
    return problemDetail
  }

  @ExceptionHandler
  fun handleInsufficientAuthenticationException(
      exception: InsufficientAuthenticationException
  ): ProblemDetail {
    val unauthorizedStatus = HttpStatus.UNAUTHORIZED
    val problemDetail = ProblemDetail.forStatus(unauthorizedStatus)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + unauthorizedStatus.value())

    if (exception.message != null) {
      problemDetail.detail = exception.message
    }
    return problemDetail
  }

  @ExceptionHandler
  fun handleCsmClientException(exception: CsmClientException): ProblemDetail {
    val badRequestStatus = HttpStatus.BAD_REQUEST
    val problemDetail = ProblemDetail.forStatus(badRequestStatus)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + badRequestStatus.value())
    problemDetail.detail = exception.message
    return problemDetail
  }

  @ExceptionHandler
  fun handleCsmResourceNotFoundException(exception: CsmResourceNotFoundException): ProblemDetail {
    val notFoundStatus = HttpStatus.NOT_FOUND
    val problemDetail = ProblemDetail.forStatus(notFoundStatus)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + notFoundStatus.value())
    problemDetail.detail = exception.message
    return problemDetail
  }

  @ExceptionHandler
  fun handleCsmAccessForbiddenException(exception: CsmAccessForbiddenException): ProblemDetail {
    val forbiddenStatus = HttpStatus.FORBIDDEN
    val problemDetail = ProblemDetail.forStatus(forbiddenStatus)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + forbiddenStatus.value())
    problemDetail.detail = exception.message
    return problemDetail
  }

  @ExceptionHandler
  fun handleBadCredentialsException(exception: BadCredentialsException): ProblemDetail {
    val badRequestStatus = HttpStatus.BAD_REQUEST
    val problemDetail = ProblemDetail.forStatus(badRequestStatus)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + badRequestStatus.value())
    problemDetail.detail = exception.message
    return problemDetail
  }

  @ExceptionHandler
  fun handleConstraintViolationException(exception: ConstraintViolationException): ProblemDetail {
    val badRequestStatus = HttpStatus.BAD_REQUEST
    val problemDetail = ProblemDetail.forStatus(badRequestStatus)
    problemDetail.type = URI.create(httpStatusCodeTypePrefix + badRequestStatus.value())
    val constraintViolations = exception.constraintViolations
    problemDetail.detail =
        constraintViolations.joinToString {
          val constraint = (it as ConstraintViolationImpl)
          "${constraint.invalidValue}:${constraint.message}"
        }
    return problemDetail
  }

  @ExceptionHandler(AuthenticationServiceException::class)
  fun handleAuthenticationServiceException(
      exception: AuthenticationServiceException
  ): ProblemDetail {
    val response = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    val internalServerErrorStatus = HttpStatus.INTERNAL_SERVER_ERROR
    response.type = URI.create(httpStatusCodeTypePrefix + internalServerErrorStatus.value())
    if (exception.message != null) {
      response.detail = exception.message
    }
    return response
  }

  @ExceptionHandler(IndexOutOfBoundsException::class)
  fun handleIndexOutOfBoundsException(exception: IndexOutOfBoundsException): ProblemDetail {
    val response = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    val internalServerErrorStatus = HttpStatus.INTERNAL_SERVER_ERROR
    response.type = URI.create(httpStatusCodeTypePrefix + internalServerErrorStatus.value())
    if (exception.message != null) {
      response.detail = exception.message
    }
    return response
  }

  @ExceptionHandler(NotImplementedException::class)
  fun handleNotImplementedException(exception: NotImplementedException): ProblemDetail {
    val response = ProblemDetail.forStatus(HttpStatus.NOT_IMPLEMENTED)
    val notImplementedErrorStatus = HttpStatus.NOT_IMPLEMENTED
    response.type = URI.create(httpStatusCodeTypePrefix + notImplementedErrorStatus.value())
    if (exception.message != null) {
      response.detail = exception.message
    }
    return response
  }

  @ExceptionHandler(NoSuchKeyException::class)
  fun handleNoSuchKeyException(): ProblemDetail {
    val response = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
    val noSuchKeyErrorStatus = HttpStatus.NOT_FOUND
    response.type = URI.create(httpStatusCodeTypePrefix + noSuchKeyErrorStatus.value())
    response.detail = "The specified file name does not exist."
    return response
  }

  @ExceptionHandler(S3Exception::class)
  fun handleS3Exception(exception: S3Exception): ProblemDetail {
    val response = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    val internalServerErrorStatus = HttpStatus.INTERNAL_SERVER_ERROR
    response.type = URI.create(httpStatusCodeTypePrefix + internalServerErrorStatus.value())
    if (exception.message != null) {
      response.detail = exception.message
    }
    return response
  }

  @ExceptionHandler
  fun handleQuantityFormatException(exception: QuantityFormatException): ProblemDetail {
    val badRequestStatus = HttpStatus.BAD_REQUEST
    val response = ProblemDetail.forStatus(badRequestStatus)
    response.type = URI.create(httpStatusCodeTypePrefix + badRequestStatus.value())
    if (exception.message != null) {
      response.detail = exception.message
    }
    return response
  }
}
