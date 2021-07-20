// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.exceptions

import java.lang.IllegalArgumentException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.NativeWebRequest
import org.zalando.problem.Problem
import org.zalando.problem.Status
import org.zalando.problem.spring.web.advice.ProblemHandling

@ControllerAdvice
internal class CsmExceptionHandling : ProblemHandling {

  override fun isCausalChainsEnabled() = true

  @ExceptionHandler
  fun handleIllegalArgumentException(
      exception: IllegalArgumentException,
      request: NativeWebRequest
  ): ResponseEntity<Problem> = create(Status.BAD_REQUEST, exception, request)
}
