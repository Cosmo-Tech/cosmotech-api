// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.storage.blob.models.BlobStorageException
import com.cosmotech.api.exceptions.CsmExceptionHandling
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.NativeWebRequest
import org.zalando.problem.Problem
import org.zalando.problem.Status

private const val HTTP_STATUS_CODE_CONFLICT = 409

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
internal class AzureExceptionHandling : CsmExceptionHandling() {

  override fun isCausalChainsEnabled() = true

  @ExceptionHandler
  fun handleBlobStorageException(
      exception: BlobStorageException,
      request: NativeWebRequest
  ): ResponseEntity<Problem> {
    val status =
        when (exception.statusCode) {
          HTTP_STATUS_CODE_CONFLICT -> Status.CONFLICT
          else -> Status.INTERNAL_SERVER_ERROR
        }
    return create(status, exception, request)
  }
}
