// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher

abstract class AbstractPhoenixService {

  @Autowired protected lateinit var objectMapper: ObjectMapper

  @Autowired protected lateinit var eventPublisher: ApplicationEventPublisher

  /**
   * Deserialize any ObjectNode as an object of the type specified. <p> This is a workaround due to
   * Azure Cosmos SDK not being able to deserialize Kotlin data classes. See
   * https://github.com/Azure/azure-sdk-for-java/issues/12269. <p> THe ObjectMapper is not
   * configured with the Kotlin Jackson Module, which is however the case with the one
   * auto-configured by SpringBoot and injected here.
   * @param objectNode the object node to deserialize
   * @return the object deserialized
   */
  @Throws(JsonProcessingException::class)
  protected inline fun <reified T> convertTo(objectNode: ObjectNode): T =
      objectMapper.treeToValue(objectNode, T::class.java)
}
