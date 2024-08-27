// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnExpression(
    "'\${csm.platform.internalResultServices.enabled}' == 'true' " +
        "and '\${csm.platform.internalResultServices.eventBus.enabled}' == 'true'")
class RabbitMqConfigModel {

  @Value("\${csm.platform.internalResultServices.eventBus.default-exchange}")
  lateinit var exchange: String

  @Value("\${csm.platform.internalResultServices.eventBus.default-queue}")
  lateinit var queue: String

  @Value("\${csm.platform.internalResultServices.eventBus.default-routing-key}")
  lateinit var routingKey: String
}
