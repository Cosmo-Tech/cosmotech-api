// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.azure.cosmos.CosmosAsyncClient
import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosClientBuilder
import com.azure.spring.autoconfigure.cosmos.CosmosHealthConfiguration
import com.azure.spring.data.cosmos.Constants
import com.cosmotech.api.utils.objectMapper
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class CsmAzureConfiguration(val cosmosClientBuilder: CosmosClientBuilder) {

  // Override the default CosmosDB ObjectMapper to support Kotlin data classes
  @Bean(name = [Constants.OBJECT_MAPPER_BEAN_NAME]) fun cosmosObjectMapper() = objectMapper()

  @Bean fun cosmosClient(): CosmosClient = cosmosClientBuilder.buildClient()
}

/**
 * The intent of this configuration class is to enable the Azure Cosmos Health Indicator, based on
 * our own 'csm.platform.vendor' property value, irrespective of the value of
 * 'management.health.azure-cosmos.enabled'.
 *
 * We actually should always enable this indicator as soon as 'csm.platform.vendor' is set to
 * 'azure'
 */
@Configuration
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class CsmAzureCosmosHealthConfiguration : CosmosHealthConfiguration() {

  @Bean
  @Primary
  override fun cosmosHealthContributor(cosmosAsyncClient: CosmosAsyncClient): HealthIndicator =
      super.cosmosHealthContributor(cosmosAsyncClient)
}
