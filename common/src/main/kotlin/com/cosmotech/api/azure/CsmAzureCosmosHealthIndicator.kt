// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.SqlQuerySpec
import com.cosmotech.api.config.CsmPlatformProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component("csmCosmos")
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class CsmAzureCosmosHealthIndicator(
    csmPlatformProperties: CsmPlatformProperties,
    private val cosmosClient: CosmosClient
) : HealthIndicator {

  private val logger = LoggerFactory.getLogger(CsmAzureCosmosHealthIndicator::class.java)
  private val coreDatabase = csmPlatformProperties.azure!!.cosmos.coreDatabase.name

  @Suppress("TooGenericExceptionCaught")
  override fun health(): Health {
    val healthBuilder =
        try {
          cosmosClient
              .getDatabase(coreDatabase)
              .getContainer("organizations")
              .queryItems(
                  SqlQuerySpec("SELECT COUNT(1) FROM c"),
                  CosmosQueryRequestOptions(),
                  Unit::class.java)
          Health.up()
        } catch (exception: Exception) {
          logger.debug("Error in health-check: {}", exception.message, exception)
          Health.down(exception)
        }
    return healthBuilder.withDetail("database", coreDatabase).build()
  }
}
