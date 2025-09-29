// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service.amqp

import com.cosmotech.common.events.WorkspaceDeleted
import com.cosmotech.run.config.RabbitMqConfigModel
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnExpression(
    "'\${csm.platform.internalResultServices.enabled}' == 'true' " +
        "and '\${csm.platform.internalResultServices.eventBus.enabled}' == 'true'")
class RemoveQueueOnWorkspaceDeletedListener(
    private val rabbitMqConfigModel: RabbitMqConfigModel,
    private val amqpClientServiceImpl: AmqpClientServiceImpl
) : ApplicationListener<WorkspaceDeleted> {

  override fun onApplicationEvent(event: WorkspaceDeleted) {
    val exchange = rabbitMqConfigModel.exchange
    amqpClientServiceImpl.removeQueue(exchange, event.workspaceId)
  }
}
