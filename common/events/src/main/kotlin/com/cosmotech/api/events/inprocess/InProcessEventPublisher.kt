// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events.inprocess

import com.cosmotech.api.events.CsmEvent
import com.cosmotech.api.events.CsmEventPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    name = ["csm.platform.event-publisher.type"], havingValue = "in_process", matchIfMissing = true)
internal class InProcessEventPublisher(private val eventPublisher: ApplicationEventPublisher) :
    CsmEventPublisher {

  override fun <T : CsmEvent> publishEvent(event: T) {
    this.eventPublisher.publishEvent(event)
  }
}
