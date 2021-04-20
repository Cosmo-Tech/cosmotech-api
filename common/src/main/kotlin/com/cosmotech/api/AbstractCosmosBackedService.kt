// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.azure.cosmos.CosmosAsyncClient
import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosClientBuilder
import com.azure.spring.data.cosmos.core.CosmosTemplate
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractCosmosBackedService : AbstractPhoenixService() {

  @Autowired protected lateinit var cosmosTemplate: CosmosTemplate

  @Autowired private lateinit var cosmosClientBuilder: CosmosClientBuilder

  protected lateinit var cosmosClient: CosmosClient
  protected lateinit var cosmosAsyncClient: CosmosAsyncClient

  @PostConstruct
  fun init() {
    this.cosmosClient = cosmosClientBuilder.buildClient()
    this.cosmosAsyncClient = cosmosClientBuilder.buildAsyncClient()
  }

  @PreDestroy
  fun destroy() {
    try {
      this.cosmosClient.close()
    } finally {
      this.cosmosAsyncClient.close()
    }
  }
}
