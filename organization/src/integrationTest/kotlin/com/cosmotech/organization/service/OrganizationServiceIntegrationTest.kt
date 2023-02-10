// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner

@ActiveProfiles(profiles = ["organization-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrganizationServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(OrganizationServiceIntegrationTest::class.java)

  @Autowired lateinit var organizationApiService: OrganizationApiService

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns "test.user@cosmotech.com"
    every { getCurrentAuthenticatedUserName() } returns "test.user"
  }

  @Test
  fun test_register_organization() {
    val organization =
        Organization(
            id = "o-organization-test",
            name = "Organization test",
            ownerId = "my.account-tester@cosmotech.com",
            security =
                OrganizationSecurity(
                    default = "none",
                    accessControlList =
                        mutableListOf(
                            OrganizationAccessControl(
                                id = "my.account-tester2@cosmotech.com", role = "reader"),
                            OrganizationAccessControl(
                                id = "my.account-tester@cosmotech.com", role = "admin"))))

    logger.info("Create new organization...")
    val organizationRegistered = organizationApiService.registerOrganization(organization)
    logger.info("New organization created : ${organizationRegistered.id}")
    logger.info("Fetch new organization created...")
    val organizationRetrieved =
        organizationApiService.findOrganizationById(organizationRegistered.id!!)
    assertEquals(organizationRegistered, organizationRetrieved)
  }
}
