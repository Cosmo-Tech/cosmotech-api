// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosClientBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class CsmAzureConfiguration(val cosmosClientBuilder: CosmosClientBuilder) {

  @Bean fun cosmosClient(): CosmosClient = cosmosClientBuilder.buildClient()
}
