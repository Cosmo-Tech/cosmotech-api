// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.consumer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMqConfigModel {

    @Value("\${props.rabbitmq.default-exchange}")
    lateinit var exchange: String

    @Value("\${props.rabbitmq.default-queue}")
    lateinit var queue: String

    @Value("\${props.rabbitmq.default-routing-key}")
    lateinit var routingKey: String
}