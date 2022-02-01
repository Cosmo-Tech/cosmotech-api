// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure.eventhubs

import com.azure.core.amqp.exception.AmqpErrorCondition
import com.azure.core.amqp.exception.AmqpException
import com.azure.messaging.eventhubs.EventHubProducerClient
import com.cosmotech.api.config.CsmPlatformProperties
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AzureEventHubsClientTests {

  @MockK(relaxed = true) private lateinit var csmPlatformProperties: CsmPlatformProperties

  private lateinit var eventHubsClient: AzureEventHubsClient

  @BeforeTest
  fun beforeTest() {
    eventHubsClient = AzureEventHubsClient(csmPlatformProperties)
  }

  @Test
  fun `PROD-7420 - doesEventHubExist returns true if the eventHub does exist`() {
    val producer = mockk<EventHubProducerClient>()
    every { producer.eventHubProperties } returns mockk()
    assertTrue { eventHubsClient.doesEventHubExist(producer) }
  }

  @Test
  fun `PROD-7420 - doesEventHubExist returns false if the eventHub does not exist`() {
    val producer = mockk<EventHubProducerClient>()
    every { producer.eventHubProperties } throws
        AmqpException(true, AmqpErrorCondition.NOT_FOUND, "Not Found", null)
    assertFalse { eventHubsClient.doesEventHubExist(producer) }
  }

  @TestFactory
  fun `PROD-7420 - doesEventHubExist throws if errors other than NOT_FOUND are reported`() =
      AmqpErrorCondition.values().filterNot { it == AmqpErrorCondition.NOT_FOUND }.map {
          amqpErrorCondition ->
        dynamicTest(amqpErrorCondition.name) {
          val producer = mockk<EventHubProducerClient>()
          every { producer.eventHubProperties } throws
              AmqpException(true, amqpErrorCondition, amqpErrorCondition.name, null)
          assertThrows<UnsupportedOperationException> {
            eventHubsClient.doesEventHubExist(producer)
          }
        }
      }

  @TestFactory
  fun `PROD-7420 - doesEventHubExist returns false if errors are ignored`() =
      AmqpErrorCondition.values().map { amqpErrorCondition ->
        dynamicTest(amqpErrorCondition.name) {
          val producer = mockk<EventHubProducerClient>()
          every { producer.eventHubProperties } throws
              AmqpException(true, amqpErrorCondition, amqpErrorCondition.name, null)
          assertFalse { eventHubsClient.doesEventHubExist(producer, ignoreErrors = true) }
        }
      }

  @Test
  fun `PROD-7420 - doesEventHubExist returns false if errors other than AMQPException are ignored`() {
    val producer = mockk<EventHubProducerClient>()
    every { producer.eventHubProperties } throws IllegalArgumentException()
    assertFalse { eventHubsClient.doesEventHubExist(producer, ignoreErrors = true) }
  }

  @Test
  fun `PROD-7420 - doesEventHubExist throws if errors other than AMQPException are not ignored`() {
    val producer = mockk<EventHubProducerClient>()
    every { producer.eventHubProperties } throws Exception()
    assertThrows<IllegalStateException> { eventHubsClient.doesEventHubExist(producer) }
  }
}
