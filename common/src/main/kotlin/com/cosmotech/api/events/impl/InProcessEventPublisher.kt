// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events.impl

import com.cosmotech.api.events.CsmEvent
import com.cosmotech.api.events.CsmEventPublisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    name = ["csm.event-publisher"], havingValue = "in-process", matchIfMissing = true)
class InProcessEventPublisher : CsmEventPublisher {

  @Autowired protected lateinit var eventPublisher: ApplicationEventPublisher

  override fun publishEvent(event: CsmEvent) {
    this.eventPublisher.publishEvent(event)
  }
}
