// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure.adx

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureDataWarehouseCluster
import com.microsoft.azure.kusto.data.Client
import com.microsoft.azure.kusto.data.KustoOperationResult
import com.microsoft.azure.kusto.data.KustoResultSetTable
import com.microsoft.azure.kusto.data.exceptions.DataClientException
import com.microsoft.azure.kusto.data.exceptions.DataServiceException
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.actuate.health.Status

@ExtendWith(MockKExtension::class)
class AzureDataExplorerClientTests {

  @MockK(relaxed = true) private lateinit var csmPlatformProperties: CsmPlatformProperties
  @MockK(relaxed = true) private lateinit var kustoClient: Client
  private lateinit var azureDataExplorerClient: AzureDataExplorerClient

  @BeforeTest
  fun beforeTest() {
    val csmPlatformPropertiesAzure = mockk<CsmPlatformAzure>()
    val csmPlatformPropertiesAzureDataWarehouseCluster =
        mockk<CsmPlatformAzureDataWarehouseCluster>()
    every { csmPlatformPropertiesAzureDataWarehouseCluster.baseUri } returns
        "https://my-datawarehouse.cluster"
    every { csmPlatformPropertiesAzure.dataWarehouseCluster } returns
        csmPlatformPropertiesAzureDataWarehouseCluster
    every { csmPlatformProperties.azure } returns csmPlatformPropertiesAzure
    this.azureDataExplorerClient = AzureDataExplorerClient(this.csmPlatformProperties)
    this.azureDataExplorerClient.setKustoClient(kustoClient)
  }

  @Test
  fun `health is DOWN if no result set from query`() {
    val result = mockk<KustoOperationResult>()
    val resultSet = mockk<KustoResultSetTable>()
    every { resultSet.next() } returns false
    every { result.primaryResults } returns resultSet
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } returns result

    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is DOWN if not healthy`() {
    val result = mockk<KustoOperationResult>()
    val resultSet = mockk<KustoResultSetTable>()
    every { resultSet.next() } returns true
    every { resultSet.getIntegerObject(eq("IsHealthy")) } returns 0
    every { result.primaryResults } returns resultSet
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } returns result

    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is DOWN if a DataServiceException is thrown while calling ADX`() {
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } throws DataServiceException("ingestionSource", "some message", true)
    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is DOWN if a DataClientException is thrown while calling ADX`() {
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } throws DataClientException("ingestionSource", "some message")
    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is DOWN if any other exception is thrown while calling ADX`() {
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } throws NullPointerException()
    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is UP if everything is Ok`() {
    val result = mockk<KustoOperationResult>()
    val resultSet = mockk<KustoResultSetTable>()
    every { resultSet.next() } returns true
    every { resultSet.getIntegerObject(eq("IsHealthy")) } returns 1
    every { result.primaryResults } returns resultSet
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } returns result

    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.UP, health.status)
  }
}
