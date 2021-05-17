// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.azure.cosmos.CosmosAsyncClient
import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosClientBuilder
import com.azure.spring.autoconfigure.cosmos.CosmosHealthConfiguration
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment

@Order(Ordered.HIGHEST_PRECEDENCE)
class CsmAzureEnvironmentPostProcessor(private val log: Log) : EnvironmentPostProcessor {

  override fun postProcessEnvironment(
      environment: ConfigurableEnvironment,
      application: SpringApplication
  ) {
    val csmPlatformVendor = System.getProperty("csm.platform.vendor")?.lowercase()
    if (!csmPlatformVendor.isNullOrBlank()) {
      // Automatically add the 'azure' Spring Profile before the application context is refreshed
      if (environment.activeProfiles.none { csmPlatformVendor.equals(it, ignoreCase = true) }) {
        log.debug("Adding '$csmPlatformVendor' as an active profile")
        environment.addActiveProfile(csmPlatformVendor)
      } else {
        log.debug("'$csmPlatformVendor' is already an active profile")
      }
    }
  }
}

@Configuration
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class CsmAzureConfiguration(val cosmosClientBuilder: CosmosClientBuilder) {

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
