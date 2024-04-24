// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.config

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory

@Configuration
@ConditionalOnExpression("'\${csm.platform.internalResultServices.enabled}' == 'true'")
class RabbitMqConfig(
    val connectionFactory: ConnectionFactory,
    val rabbitMqConfigModel: RabbitMqConfigModel
) : RabbitListenerConfigurer {

  @Bean
  fun defaultQueue(): Queue {
    return Queue(rabbitMqConfigModel.queue, true)
  }

  @Bean
  fun defaultExchange(): TopicExchange {
    return TopicExchange(rabbitMqConfigModel.exchange)
  }

  @Bean
  fun defaultBinding(): Binding {
    return BindingBuilder.bind(defaultQueue())
        .to(defaultExchange())
        .with(rabbitMqConfigModel.routingKey)
  }

  @Bean fun rabbitTemplate() = RabbitTemplate(connectionFactory)

  @Bean fun rabbitAdmin() = RabbitAdmin(connectionFactory)

  @Bean fun rabbitListenerEndpointRegistry() = RabbitListenerEndpointRegistry()

  @Bean fun messageHandlerMethodFactory() = DefaultMessageHandlerMethodFactory()

  override fun configureRabbitListeners(registrar: RabbitListenerEndpointRegistrar) {
    val factory = SimpleRabbitListenerContainerFactory()
    factory.setPrefetchCount(1)
    factory.setConsecutiveActiveTrigger(1)
    factory.setConsecutiveIdleTrigger(1)
    factory.setConnectionFactory(connectionFactory)
    registrar.setContainerFactory(factory)
    registrar.setEndpointRegistry(rabbitListenerEndpointRegistry())
    registrar.messageHandlerMethodFactory = messageHandlerMethodFactory()
  }
}
