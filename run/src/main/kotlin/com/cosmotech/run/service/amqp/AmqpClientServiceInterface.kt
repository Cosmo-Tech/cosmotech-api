// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service.amqp

interface AmqpClientServiceInterface {

  fun addNewQueue(exchangeName: String, queueName: String)

  fun removeQueue(exchangeName: String, queueName: String)
}
