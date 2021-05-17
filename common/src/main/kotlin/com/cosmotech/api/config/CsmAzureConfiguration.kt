// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosClientBuilder
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import org.apache.commons.logging.Log
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.env.EnvironmentPostProcessor
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
class CsmAzureConfiguration(
    private val cosmosClientBuilder: CosmosClientBuilder,
    private val blobServiceClientBuilder: BlobServiceClientBuilder,
) {

  @Bean fun cosmosClient(): CosmosClient = cosmosClientBuilder.buildClient()

  @Bean fun storageClient(): BlobServiceClient = blobServiceClientBuilder.buildClient()
}
