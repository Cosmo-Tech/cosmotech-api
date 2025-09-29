// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service.amqp

import com.cosmotech.common.events.RunStart
import com.cosmotech.run.config.RabbitMqConfigModel
import com.cosmotech.runner.domain.Runner
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnExpression(
    "'\${csm.platform.internalResultServices.enabled}' == 'true' " +
        "and '\${csm.platform.internalResultServices.eventBus.enabled}' == 'true'")
class AddQueueOnRunStartListener(
    private val rabbitMqConfigModel: RabbitMqConfigModel,
    private val amqpClientServiceImpl: AmqpClientServiceImpl,
) : ApplicationListener<RunStart> {

  override fun onApplicationEvent(event: RunStart) {
    val exchange = rabbitMqConfigModel.exchange
    amqpClientServiceImpl.addNewQueue(exchange, (event.runnerData as Runner).workspaceId)
  }
}
