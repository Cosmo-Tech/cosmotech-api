// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosDatabase
import com.azure.spring.data.cosmos.core.CosmosTemplate
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractCosmosBackedService : AbstractPhoenixService() {

  @Autowired protected lateinit var cosmosTemplate: CosmosTemplate

  @Autowired protected lateinit var cosmosClient: CosmosClient

  protected lateinit var cosmosCoreDatabase: CosmosDatabase

  @PostConstruct
  fun init() {
    this.cosmosCoreDatabase =
        cosmosClient.getDatabase(csmPlatformProperties.azure!!.cosmos.coreDatabase.name)
  }
}
