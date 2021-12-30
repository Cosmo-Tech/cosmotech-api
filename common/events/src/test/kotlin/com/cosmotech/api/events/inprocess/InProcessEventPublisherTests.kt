// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events.inprocess

import com.cosmotech.api.events.OrganizationRegistered
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.springframework.context.ApplicationEventPublisher

class InProcessEventPublisherTests {

  private lateinit var eventPublisher: ApplicationEventPublisher
  private lateinit var inProcessEventPublisher: InProcessEventPublisher

  @BeforeTest
  fun beforeTest() {
    this.eventPublisher = mockk(relaxUnitFun = true)
    this.inProcessEventPublisher = InProcessEventPublisher(this.eventPublisher)
  }

  @Test
  fun `Spring EventPublisher gets called when publishing CsmEvents`() {
    val event = OrganizationRegistered(this, "fake_organization_id")

    this.inProcessEventPublisher.publishEvent(event)

    verify(exactly = 1) { eventPublisher.publishEvent(event) }
    confirmVerified(this.eventPublisher)
  }
}
