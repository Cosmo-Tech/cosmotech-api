// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.storage.blob.models.BlobStorageException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.NativeWebRequest
import org.zalando.problem.Problem
import org.zalando.problem.Status
import org.zalando.problem.spring.web.advice.ProblemHandling

@ControllerAdvice
class AzureExceptionHandling : ProblemHandling {

  override fun isCausalChainsEnabled() = true

  @ExceptionHandler
  fun handleBlobStorageException(
      exception: BlobStorageException,
      request: NativeWebRequest
  ): ResponseEntity<Problem> {
    val status =
        when (exception.statusCode) {
          409 -> Status.CONFLICT
          else -> Status.INTERNAL_SERVER_ERROR
        }
    return create(status, exception, request)
  }
}
