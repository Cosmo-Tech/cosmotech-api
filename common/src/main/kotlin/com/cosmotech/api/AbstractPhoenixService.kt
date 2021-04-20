// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher

abstract class AbstractPhoenixService {

  @Autowired protected lateinit var eventPublisher: ApplicationEventPublisher
}
