// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.consumer

interface AmqpClientServiceInterface {

    fun addNewQueue(queueName:String)
    fun removeNewQueue(exchangeName: String, queueName:String)
}
