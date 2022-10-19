// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.azure

import com.azure.core.exception.ResourceNotFoundException
import com.azure.cosmos.CosmosDatabase
import com.azure.spring.data.cosmos.core.CosmosTemplate
import com.cosmotech.api.azure.findByIdOrThrow
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

const val ORGANIZATION_ID = "O-AbCdEf123"
const val USER_ID = "bob@mycompany.com"

@ExtendWith(MockKExtension::class)
class OrganizationServiceImplTests {

  @Suppress("unused") @MockK private lateinit var csmAdmin: CsmAdmin
  @MockK private lateinit var csmRbac: CsmRbac
  @Suppress("unused") @MockK private lateinit var csmPlatformProperties: CsmPlatformProperties
  @Suppress("unused") @MockK private var cosmosTemplate: CosmosTemplate = mockk(relaxed = true)
  @Suppress("unused") @MockK private var cosmosCoreDatabase: CosmosDatabase = mockk()
  @Suppress("unused")
  @MockK
  private var coreOrganizationContainer = mockkStatic("kotlin.text.StringsKt")
  @InjectMockKs lateinit var organizationServiceImpl: OrganizationServiceImpl

  @BeforeEach
  fun setUp() {
    MockKAnnotations.init(this)
    every { organizationServiceImpl.initService() } returns Unit
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
    every { cosmosTemplate.findByIdOrThrow<Organization, String>(any(), any()) } returns
        organization
    every { csmRbac.verify(any(), any()) } returns Unit
    every { csmRbac.checkUserExists(any(), any(), any()) } returns rbacAccessControl
    every { csmRbac.setUserRole(any(), any(), any()) } returns rbacSecurity

    assertEquals(organization.security?.default, rbacSecurity.default)
    assertEquals(
        organization.security!!.accessControlList[0].id, rbacSecurity.accessControlList[0].id)
    assertEquals(
        organization.security!!.accessControlList[0].role, rbacSecurity.accessControlList[0].role)

    every { cosmosTemplate.upsertAndReturnEntity<Organization>(any(), any()) } returns organization
    every { csmRbac.getAccessControl(any(), any()) } returns rbacAccessControl
    var organizationAccessControl =
        organizationServiceImpl.updateOrganizationAccessControl(
            ORGANIZATION_ID, USER_ID, organizationRole)
    assertEquals(organizationAccessControl.id, rbacAccessControl.id)
    assertEquals(organizationAccessControl.role, rbacAccessControl.role)
  }

  @Test
  fun `should throw ResourceNotFound if getAccessControl throws it`() {
    val organization = getMockOrganization()
    every { cosmosTemplate.findByIdOrThrow<Organization, String>(any(), any()) } returns
        organization
    every { csmRbac.verify(any(), any()) } returns Unit
    every { csmRbac.checkUserExists(any(), any(), any()) } throws mockk<ResourceNotFoundException>()
    val organizationRole = OrganizationRole(role = ROLE_VIEWER)
    assertThrows<ResourceNotFoundException> {
      organizationServiceImpl.updateOrganizationAccessControl(
          ORGANIZATION_ID, USER_ID, organizationRole)
    }
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
