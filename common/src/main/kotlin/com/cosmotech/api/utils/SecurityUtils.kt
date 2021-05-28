// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import org.springframework.security.core.context.SecurityContextHolder

fun getCurrentUserId(): String = SecurityContextHolder.getContext().authentication.name
