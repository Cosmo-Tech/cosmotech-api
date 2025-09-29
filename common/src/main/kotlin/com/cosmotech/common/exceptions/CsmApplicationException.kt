// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
open class CsmApplicationException(message: String? = null, cause: Throwable? = null) :
    RuntimeException(message, cause)
