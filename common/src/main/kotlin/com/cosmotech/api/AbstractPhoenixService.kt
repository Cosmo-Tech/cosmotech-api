// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.cosmotech.api.events.CsmEventPublisher
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractPhoenixService {

  @Autowired protected lateinit var eventPublisher: CsmEventPublisher
}
