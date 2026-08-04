// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.com.cosmotech.iaminfo.service

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.security.ROLE_ORGANIZATION_USER
import com.cosmotech.common.security.ROLE_ORGANIZATION_VIEWER
import com.cosmotech.common.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.common.tests.CsmTestBase
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.common.utils.getCurrentAuthenticatedRoles
import com.cosmotech.iaminfo.IAMInfoApiServiceInterface
import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles(profiles = ["iaminfo-test"])
@ExtendWith(MockKExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableRedisDocumentRepositories(basePackages = ["com.cosmotech"])
@Suppress("FunctionName")
class IAMInfoServiceRBACTest : CsmTestBase() {
  val TEST_USER_MAIL = "testuser@mail.fr"

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Autowired lateinit var iaminfoApiService: IAMInfoApiServiceInterface

  @BeforeAll
  fun globalSetup() {
    mockkStatic("com.cosmotech.common.utils.SecurityUtilsKt")
  }

  @BeforeEach
  fun setup() {
    every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL
  }

  @TestFactory
  fun `test Platform admin can list iam members`() =
      mapOf(
              ROLE_PLATFORM_ADMIN to false,
              ROLE_ORGANIZATION_USER to true,
              ROLE_ORGANIZATION_VIEWER to true,
              ROLE_NONE to true,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC listIAMMembers : $role") {
              every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      iaminfoApiService.listIAMMembers()
                    }
                assertEquals(
                    "User does not have permission $ROLE_PLATFORM_ADMIN",
                    exception.message,
                )
              } else {
                assertDoesNotThrow { iaminfoApiService.listIAMMembers() }
              }
            }
          }

  // should throw every time, only ROLE_PLATFORM_ADMIN can list keycloak members
  @TestFactory
  fun `test RBAC listIAMMembers`() =
      mapOf(
              ROLE_PLATFORM_ADMIN to false,
              ROLE_ORGANIZATION_USER to false,
              ROLE_ORGANIZATION_VIEWER to false,
              ROLE_NONE to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC listIAMMembers : $role") {
              every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      iaminfoApiService.listIAMGroups()
                    }
                assertEquals(
                    "User does not have permission $ROLE_PLATFORM_ADMIN",
                    exception.message,
                )
              } else {
                assertDoesNotThrow { iaminfoApiService.listIAMGroups() }
              }
            }
          }
}
