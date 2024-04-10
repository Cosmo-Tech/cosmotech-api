// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.consumer

import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class AmqpClientServiceImpl(
    private val workspaceApiService: WorkspaceApiServiceInterface,
    private val organizationApiService: OrganizationApiServiceInterface,
    private val rabbitAdmin: RabbitAdmin,
    private val rabbitListenerEndpointRegistry: RabbitListenerEndpointRegistry,
    private val rabbitMqConfigModel: RabbitMqConfigModel
) : AmqpClientServiceInterface {

  val workspaceKeys = mutableSetOf<String>()

  private val logger = LoggerFactory.getLogger(AmqpClientServiceImpl::class.java)

  //TODO add loggers
  //TODO define Pageable for getAllOrganizationIds and getWorkspaceIds
  //TODO organize files and functions
  //TODO remove exchange param and use props.* by default
  //TODO Rename props.* with human readable name
  //TODO Change/Remove Jackson Message Consumer, as message from simulation are binaries
  //TODO Add tests
  //TODO Add queue create on workspace creation (event based)
  //TODO Add listener on /start call (event based)
  @EventListener(ApplicationReadyEvent::class)
  fun retrieveQueueNames() {
    val organizations = organizationApiService.getAllOrganizationIds()
    organizations.forEach {
      val workspacesIds = workspaceApiService.getWorkspaceIds(it)
      workspaceKeys.addAll(workspacesIds)
      workspacesIds.forEach{
        this.addNewQueue(it)
      }
    }
  }


  @RabbitListener(id ="\${props.rabbitmq.default-exchange}", queues=["\${props.rabbitmq.default-queue}"], concurrency = "2")
  fun receive(message: String) {
    logger.info(message)
  }



  override fun addNewQueue(queueName: String) {
    //TODO pass durable to true (in case of broker restart) => adapt Proton client
    val queue = Queue(queueName,false,false,false)
    val binding = Binding(queueName,Binding.DestinationType.QUEUE, rabbitMqConfigModel.exchange,queueName,null)
    rabbitAdmin.declareQueue(queue)
    rabbitAdmin.declareBinding(binding)
    this.addQueueToListener(rabbitMqConfigModel.exchange,queueName)
  }


  private fun addQueueToListener(exchangeName: String, queueName: String ) {
    if(!checkQueueExistOnListener(exchangeName, queueName)) {
      val listenerContainer = this.getMessageListenerContainerById(exchangeName)
      if(listenerContainer != null) {
        listenerContainer.addQueueNames(queueName)
        logger.info("Add exchange/queue: $exchangeName/$queueName to listener")
      }
    }
  }

  private fun checkQueueExistOnListener(exchangeName: String, queueName: String): Boolean {
    val listenerContainer = this.getMessageListenerContainerById(exchangeName)
    if (listenerContainer != null){
      val existingQueueNames = listenerContainer.queueNames
      return existingQueueNames.contains(queueName)
    }
    return false
  }

  private fun getMessageListenerContainerById(exchangeName: String) =
    this.rabbitListenerEndpointRegistry.getListenerContainer(exchangeName) as AbstractMessageListenerContainer?

  override fun removeNewQueue(exchangeName: String, queueName: String) {
    if(checkQueueExistOnListener(exchangeName, queueName)) {
      getMessageListenerContainerById(exchangeName)?.removeQueueNames(queueName)
      this.rabbitAdmin.deleteQueue(queueName)
    }
  }
}
