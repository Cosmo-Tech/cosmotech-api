// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
open class CsmServerException(override val message: String, override val cause: Throwable? = null) :
    CsmApplicationException(message, cause)

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
class CsmServiceUnavailableException(
    override val message: String,
    override val cause: Throwable? = null
) : CsmServerException(message, cause)
