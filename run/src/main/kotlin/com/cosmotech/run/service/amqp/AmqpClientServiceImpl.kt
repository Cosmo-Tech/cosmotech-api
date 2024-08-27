// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service.amqp

import com.cosmotech.api.events.RunStart
import com.cosmotech.api.events.WorkspaceDeleted
import com.cosmotech.run.config.RabbitMqConfigModel
import com.cosmotech.run.service.RunServiceImpl
import com.cosmotech.runner.domain.Runner
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Suppress("ConstructorParameterNaming")
data class ProbeMessage(
    val simulation: Map<String, Any>,
    val probe: Map<String, Any>,
    val facts_common: Map<String, Any>,
    val facts: List<Map<String, Any>>
)

@Service
@ConditionalOnExpression(
    "'\${csm.platform.internalResultServices.enabled}' == 'true' " +
        "and '\${csm.platform.internalResultServices.eventBus.enabled}' == 'true'")
class AmqpClientServiceImpl(
    private val rabbitAdmin: RabbitAdmin,
    private val rabbitListenerEndpointRegistry: RabbitListenerEndpointRegistry,
    private val rabbitMqConfigModel: RabbitMqConfigModel,
    private val runServiceImpl: RunServiceImpl
) : AmqpClientServiceInterface {

  private val logger = LoggerFactory.getLogger(AmqpClientServiceImpl::class.java)

  @EventListener(RunStart::class)
  fun awakeListener(event: RunStart) {
    val exchange = rabbitMqConfigModel.exchange
    this.addNewQueue(exchange, (event.runnerData as Runner).workspaceId!!)
  }

  @EventListener(WorkspaceDeleted::class)
  fun removeQueueFromDeletedWorkspace(event: WorkspaceDeleted) {
    val exchange = rabbitMqConfigModel.exchange
    this.removeQueue(exchange, event.workspaceId)
  }

  @RabbitListener(
      id = "\${csm.platform.internalResultServices.eventBus.default-exchange}",
      queues = ["\${csm.platform.internalResultServices.eventBus.default-queue}"],
      concurrency = "5")
  fun receive(message: Message) {
    logger.debug("Message received...")
    val gson = Gson()
    val mapAdapter = gson.getAdapter(object : TypeToken<ProbeMessage>() {})
    val messageRead =
        mapAdapter.fromJson(
            ByteArrayInputStream(message.body).use { bais ->
              GZIPInputStream(bais).use { gis ->
                InputStreamReader(gis, Charsets.UTF_8).use { isr ->
                  BufferedReader(isr).use { bfr -> bfr.readText() }
                }
              }
            })
    val data = mutableListOf<Map<String, Any>>()
    messageRead.facts.forEach { it ->
      val row = (it + messageRead.facts_common).toMutableMap()
      row["probe_name"] = messageRead.probe["name"].toString()
      row["probe_run"] = messageRead.probe["run"]!!
      data.add(row)
    }
    val runId = messageRead.simulation["run"].toString()
    val tableName = messageRead.probe["type"].toString()
    runServiceImpl.sendDataToStorage(runId, tableName, data, true)
  }

  override fun addNewQueue(exchangeName: String, queueName: String) {
    logger.debug("Adding Queue $exchangeName/$queueName to broker")
    // TODO pass durable to true (in case of broker restart) => adapt Proton client
    val queue = Queue(queueName, false, false, false)
    val binding = Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, queueName, null)
    rabbitAdmin.declareQueue(queue)
    rabbitAdmin.declareBinding(binding)
    logger.debug("Queue $exchangeName/$queueName added to broker")
    this.addQueueToListener(exchangeName, queueName)
  }

  private fun addQueueToListener(exchangeName: String, queueName: String) {
    logger.debug("Trying to add Queue $exchangeName/$queueName to listener")
    if (!checkQueueExistOnListener(exchangeName, queueName)) {
      logger.debug("Queue $exchangeName/$queueName is not handled by a listener...")
      val listenerContainer = this.getMessageListenerContainerById(exchangeName)
      if (listenerContainer != null) {
        listenerContainer.addQueueNames(queueName)
        logger.debug("Queue $exchangeName/$queueName added to listener")
      }
    }
  }

  private fun checkQueueExistOnListener(exchangeName: String, queueName: String): Boolean {
    val listenerContainer = this.getMessageListenerContainerById(exchangeName)
    if (listenerContainer != null) {
      val existingQueueNames = listenerContainer.queueNames
      return existingQueueNames.contains(queueName)
    }
    logger.debug("No listener defined for couple exchange/queueName $exchangeName/$queueName")
    return false
  }

  private fun getMessageListenerContainerById(exchangeName: String) =
      this.rabbitListenerEndpointRegistry.getListenerContainer(exchangeName)
          as AbstractMessageListenerContainer?

  override fun removeQueue(exchangeName: String, queueName: String) {
    logger.debug("Trying to remove Queue $exchangeName/$queueName to listener")
    if (checkQueueExistOnListener(exchangeName, queueName)) {
      logger.debug("Queue $exchangeName/$queueName is handled by a listener")
      getMessageListenerContainerById(exchangeName)?.removeQueueNames(queueName)
    }
    this.rabbitAdmin.deleteQueue(queueName)
  }
}
