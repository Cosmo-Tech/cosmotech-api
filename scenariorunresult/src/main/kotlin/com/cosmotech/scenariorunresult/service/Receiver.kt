// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.service

import org.json.JSONException
import org.json.JSONObject
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import redis.clients.jedis.JedisPool
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch

@Component
class Receiver(var jedisPool: JedisPool) {

  private var latch = CountDownLatch(1)
  private var queueName = "probesmeasures"
  private var topicExchangeName = "csm"

  fun receiveMessage(message: ByteArray) {
    val messageToString = message.toString(Charsets.UTF_8)
    val keyResult = createKeyResult()
    val date = LocalDateTime.now().format(DateTimeFormatter.ISO_TIME)
    try {
      JSONObject(messageToString)
      println("Receiver <${messageToString}>")
      jedisPool.resource.use {
          jedisClient -> jedisClient.eval( "redis.call('JSON.SET',KEYS[1],KEYS[2],KEYS[3]);",3,keyResult+"json","$",messageToString)
      }
    } catch (e: JSONException) {
      println("Ce n'est pas un json")
      jedisPool.resource.use {
          jedisClient -> jedisClient.eval("redis.call('HSET',KEYS[1],KEYS[2],KEYS[3]);",3,keyResult+date,"message",messageToString)
      }
    }

    latch.countDown()
  }

  private fun createKeyResult(): String {
    return "O-gZYpnd27G7:w-70klgqeroooz:s-l1g1z2jyng0:sr-jreyXXXX:p-jreyYYYY"
  }

  @Bean
  fun queue(): Queue {
    return Queue(queueName, false)
  }

  @Bean
  fun exchange(): TopicExchange {
    return TopicExchange(topicExchangeName)
  }

  @Bean
  fun binding(queue: Queue?, exchange: TopicExchange): Binding {
    return BindingBuilder.bind(queue).to(exchange).with("foo.bar.#")
  }

  @Bean
  fun container(
      connectionFactory: ConnectionFactory,
      listenerAdapter: MessageListenerAdapter
  ): SimpleMessageListenerContainer {
    val container = SimpleMessageListenerContainer()
    container.connectionFactory = connectionFactory
    container.setQueueNames(queueName)
    container.setMessageListener(listenerAdapter)
    return container
  }

  @Bean
  fun listenerAdapter(receiver: Receiver): MessageListenerAdapter {
    return MessageListenerAdapter(receiver, "receiveMessage")
  }
}
