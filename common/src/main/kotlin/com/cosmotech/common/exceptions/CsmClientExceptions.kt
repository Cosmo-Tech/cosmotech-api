// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.BAD_REQUEST)
open class CsmClientException(override val message: String, override val cause: Throwable? = null) :
    CsmApplicationException(message, cause)

@ResponseStatus(HttpStatus.NOT_FOUND)
class CsmResourceNotFoundException(
    override val message: String,
    override val cause: Throwable? = null
) : CsmClientException(message, cause)

@ResponseStatus(HttpStatus.FORBIDDEN)
class CsmAccessForbiddenException(
    override val message: String,
    override val cause: Throwable? = null
) : CsmClientException(message, cause)
