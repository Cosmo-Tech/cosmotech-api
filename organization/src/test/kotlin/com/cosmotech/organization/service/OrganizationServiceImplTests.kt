// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.domain.OrganizationService
import com.cosmotech.organization.domain.OrganizationServices
import com.cosmotech.organization.repository.OrganizationRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.testcontainers.shaded.org.bouncycastle.asn1.x509.X509ObjectIdentifiers.organization

const val ORGANIZATION_ID = "O-AbCdEf123"
const val USER_ID = "bob@mycompany.com"

@ExtendWith(MockKExtension::class)
class OrganizationServiceImplTests {

  @Suppress("unused") @MockK private var eventPublisher: CsmEventPublisher = mockk(relaxed = true)
  @Suppress("unused") @MockK private var idGenerator: CsmIdGenerator = mockk(relaxed = true)

  @Suppress("unused")
  @MockK
  private var csmPlatformProperties: CsmPlatformProperties = mockk(relaxed = true)
  @Suppress("unused") @MockK private var csmAdmin: CsmAdmin = CsmAdmin(csmPlatformProperties)
  @SpyK private var csmRbac: CsmRbac = CsmRbac(csmPlatformProperties, csmAdmin)

  @MockK private var organizationRepository: OrganizationRepository = mockk(relaxed = true)
  @InjectMockKs lateinit var organizationApiService: OrganizationServiceImpl

  @BeforeEach
  fun setUp() {
    every { csmPlatformProperties.rbac.enabled } returns true

    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns USER_ID
    every { getCurrentAuthenticatedUserName() } returns "my.account-tester"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    MockKAnnotations.init(this)
  }

  @Test
  fun `should update organization control & returns Organization Access control`() {
    val organization = getMockOrganization()
    val organizationRole = OrganizationRole(role = ROLE_VIEWER)
    val rbacSecurity =
        RbacSecurity(
            organization.id,
            organization.security!!.default,
            mutableListOf(RbacAccessControl("ID", ROLE_VIEWER)))
    val rbacAccessControl = RbacAccessControl(USER_ID, ROLE_ADMIN)
    every { organizationRepository.findByIdOrNull(any()) } returns organization
    every { csmRbac.verify(any(), any()) } returns Unit
    every { csmRbac.checkUserExists(any(), any(), any()) } returns rbacAccessControl
    every { csmRbac.setUserRole(any(), any(), any()) } returns rbacSecurity

    assertEquals(organization.security?.default, rbacSecurity.default)
    assertEquals(
        organization.security!!.accessControlList[0].id, rbacSecurity.accessControlList[0].id)
    assertEquals(
        organization.security!!.accessControlList[0].role, rbacSecurity.accessControlList[0].role)

    every { organizationRepository.save(any()) } returns organization
    every { csmRbac.getAccessControl(any(), any()) } returns rbacAccessControl
    var organizationAccessControl =
        organizationApiService.updateOrganizationAccessControl(
            ORGANIZATION_ID, USER_ID, organizationRole)
    assertEquals(organizationAccessControl.id, rbacAccessControl.id)
    assertEquals(organizationAccessControl.role, rbacAccessControl.role)
  }

  @Test
  fun `should throw ResourceNotFound if getAccessControl throws it`() {
    val organization = getMockOrganization()
    every { organizationRepository.findByIdOrNull(any()) } returns organization
    every { csmRbac.verify(any(), any()) } returns Unit
    every { csmRbac.checkUserExists(any(), any(), any()) } throws
        mockk<CsmResourceNotFoundException>()
    val organizationRole = OrganizationRole(role = ROLE_VIEWER)
    assertThrows<CsmResourceNotFoundException> {
      organizationApiService.updateOrganizationAccessControl(
          ORGANIZATION_ID, USER_ID, organizationRole)
    }
  }

  @Test
  fun `should test import Organization method and assert it registered`() {
    val organization = getMockOrganization()
    every { organizationRepository.save(any()) } returns organization
    val importedScenario = organizationApiService.importOrganization(organization)
    assertEquals(organization, importedScenario)
  }

  @Test
  fun `should test import Organization method and assert it throws exception when id scenario is missing`() {
    val organization = getMockOrganization()
    organization.id = null
    assertThrows<CsmResourceNotFoundException> {
      organizationApiService.importOrganization(organization)
    }
  }

  @TestFactory
  fun `test RBAC read organization`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC read: $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              organizationApiService.findOrganizationById(it.id!!)
            }
          }

  @TestFactory
  fun `test RBAC unregister organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC unregister : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.delete(any()) } returns Unit
              organizationApiService.unregisterOrganization(it.id!!)
            }
          }

  @TestFactory
  fun `test RBAC update organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC update : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.save(any()) } returns it
              organizationApiService.updateOrganization(
                  it.id!!, makeOrganizationWithRole(it.id!!, "modifiedOrganization", role))
            }
          }

  @TestFactory
  fun `test RBAC updateTenantCredentials organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC updateTenantCredentials : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.save(any()) } returns it
              organizationApiService.updateTenantCredentialsByOrganizationId(
                  it.id!!, mapOf(Pair("", "")))
            }
          }

  @TestFactory
  fun `test RBAC updateStorageConfiguration organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC updateStorageConfiguration : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.save(any()) } returns it
              organizationApiService.updateStorageByOrganizationId(it.id!!, OrganizationService())
            }
          }

  @TestFactory
  fun `test RBAC updateSolutionsContainerRegistry organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC updateSolutionsContainerRegistry : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.save(any()) } returns it
              organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
                  it.id!!, OrganizationService())
            }
          }

  @TestFactory
  fun `test RBAC updateStorage organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC updateStorage : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.save(any()) } returns it
              organizationApiService.updateStorageByOrganizationId(it.id!!, OrganizationService())
            }
          }

  @TestFactory
  fun `test RBAC getOrganizationSecurity organization`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC getOrganizationSecurity : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              organizationApiService.getOrganizationSecurity(it.id!!)
            }
          }

  @TestFactory
  fun `test RBAC setOrganizationDefaultSecurity organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC setOrganizationDefaultSecurity : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.save(any()) } returns it
              organizationApiService.setOrganizationDefaultSecurity(it.id!!, OrganizationRole(role))
            }
          }

  @TestFactory
  fun `test RBAC getOrganizationAccessControl organization`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC getOrganizationAccessControl : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              organizationApiService.getOrganizationAccessControl(it.id!!, USER_ID)
            }
          }

  @TestFactory
  fun `test RBAC addOrganizationAccessControl organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC addOrganizationAccessControl : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.save(any()) } returns it
              organizationApiService.addOrganizationAccessControl(
                  it.id!!, OrganizationAccessControl("id", "viewer"))
            }
          }

  @TestFactory
  fun `test RBAC updateOrganizationAccessControl organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC updateOrganizationAccessControl : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.save(any()) } returns it
              organizationApiService.updateOrganizationAccessControl(
                  it.id!!, "2$USER_ID", OrganizationRole("user"))
            }
          }

  @TestFactory
  fun `test RBAC removeOrganizationAccessControl organization`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC removeOrganizationAccessControl  : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              every { organizationRepository.save(any()) } returns it
              organizationApiService.removeOrganizationAccessControl(it.id!!, "2$USER_ID")
            }
          }

  @TestFactory
  fun `test getOrganizationSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC get users with role : $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it
              organizationApiService.getOrganizationSecurityUsers(it.id!!)
            }
          }

  private fun rbacTest(
      name: String,
      role: String,
      shouldThrow: Boolean,
      testLambda: (organization: Organization) -> Unit
  ): DynamicTest? {
    val organization = makeOrganizationWithRole("id", USER_ID, role)
    return DynamicTest.dynamicTest(name) {
      if (shouldThrow) {
        assertThrows<CsmAccessForbiddenException> { testLambda(organization) }
      } else {
        assertDoesNotThrow { testLambda(organization) }
      }
    }
  }

  fun makeOrganizationWithRole(id: String, name: String, role: String): Organization {
    return Organization(
        id = id,
        name = name,
        ownerId = name,
        services =
            OrganizationServices(
                tenantCredentials = mutableMapOf(),
                storage =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair(name, role))),
                solutionsContainerRegistry =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair(name, role)))),
        security =
            OrganizationSecurity(
                default = "none",
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(name, role),
                        OrganizationAccessControl("2$name", "viewer"))))
  }

  fun getMockOrganization(): Organization {
    val organization = Organization()
    organization.id = ORGANIZATION_ID
    val organizationSecurity =
        OrganizationSecurity(
            ROLE_VIEWER, mutableListOf(OrganizationAccessControl("ID", ROLE_VIEWER)))
    organization.security = organizationSecurity
    return organization
  }
}
