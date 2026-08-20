// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.security.keycloak

import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.model.RbacAccessControl
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.GroupResource
import org.keycloak.admin.client.resource.GroupsResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.RoleMappingResource
import org.keycloak.admin.client.resource.RoleScopeResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation

class KeycloakClientTests {

  private var keycloakClient: KeycloakClient =
      spyk(
          KeycloakClient(
              csmPlatformProperties = mockk(),
              serverUrl = "http://localhost:8080",
              realm = "cosmotech",
              adminClientId = "admin-cli",
              username = "admin",
              password = "admin",
          )
      )

  private var keycloakInstance: Keycloak = mockk<Keycloak>()
  private var realmResource: RealmResource = mockk<RealmResource>()
  private var groupsResource: GroupsResource = mockk<GroupsResource>()
  private var usersResource: UsersResource = mockk<UsersResource>()

  @BeforeEach
  fun setUp() {
    every { keycloakClient.getKeycloakInstance() } returns keycloakInstance
    every { keycloakInstance.realm(any()) } returns realmResource
    every { realmResource.groups() } returns groupsResource
    every { realmResource.users() } returns usersResource
  }

  @Test
  fun `isGroupPublic - getAllGroups filters out groups without public attribute`() {
    val privateGroup = makeGroup("private-group", id = "id-private", attributes = null)
    val publicGroup =
        makeGroup(
            "public-group",
            id = "id-public",
            attributes = mapOf("public" to listOf("true")),
        )
    every { groupsResource.count(true) } returns mapOf("count" to 2L)
    every { groupsResource.groups(any(), any(), any(), any()) } returns
        listOf(privateGroup, publicGroup)

    val result = keycloakClient.getAllGroups()

    assertEquals(1, result.size)
    assertEquals("public-group", result[0].name)
  }

  @Test
  fun `isGroupPublic - getAllGroups filters out groups with public attribute set to false`() {
    val falsePublicGroup = makeGroup("false-group", attributes = mapOf("public" to listOf("false")))
    every { groupsResource.count(true) } returns mapOf("count" to 1L)
    every { groupsResource.groups(any(), any(), any(), any()) } returns listOf(falsePublicGroup)

    val result = keycloakClient.getAllGroups()

    assertTrue(result.isEmpty())
  }

  @Test
  fun `isGroupPublic - getAllGroups keeps groups with public attribute set to true`() {
    val publicGroup = makeGroup("public-group", attributes = mapOf("public" to listOf("true")))
    every { groupsResource.count(true) } returns mapOf("count" to 1L)
    every { groupsResource.groups(any(), any(), any(), any()) } returns listOf(publicGroup)

    val result = keycloakClient.getAllGroups()

    assertEquals(1, result.size)
    assertEquals("public-group", result[0].name)
  }

  @Test
  fun `isGroupPublic - getUsersInGroup throws when group is not public`() {
    val privateGroup = makeGroup("private-group", id = "id-private", attributes = null)
    val groupResource = mockk<GroupResource>()
    every { groupsResource.group("id-private") } returns groupResource
    every { groupResource.toRepresentation() } returns privateGroup

    assertThrows<CsmResourceNotFoundException> {
      keycloakClient.getUsersInGroup("id-private")
    }
  }

  @Test
  fun `getUsersInGroup returns members of a public group`() {
    val publicGroup =
        makeGroup("analysts", id = "id-analysts", attributes = mapOf("public" to listOf("true")))
    val members =
        listOf(
            makeUser("alice@cosmotech.com"),
            makeUser("bob@cosmotech.com"),
        )
    val groupResource = mockk<GroupResource>()
    every { groupsResource.group("id-analysts") } returns groupResource
    every { groupResource.toRepresentation() } returns publicGroup
    every { groupResource.members() } returns members

    val result = keycloakClient.getUsersInGroup("id-analysts")

    assertEquals(2, result.size)
    assertEquals("alice@cosmotech.com", result[0].username)
    assertEquals("bob@cosmotech.com", result[1].username)
  }

  @Test
  fun `getUsersInGroup returns empty list when public group has no members`() {
    val publicGroup =
        makeGroup("empty-group", id = "id-empty", attributes = mapOf("public" to listOf("true")))
    val groupResource = mockk<GroupResource>()
    every { groupsResource.group("id-empty") } returns groupResource
    every { groupResource.toRepresentation() } returns publicGroup
    every { groupResource.members() } returns emptyList()

    val result = keycloakClient.getUsersInGroup("id-empty")

    assertTrue(result.isEmpty())
  }

  @Test
  fun `getUsersInGroup throws CsmResourceNotFoundException for private group`() {
    val privateGroup =
        makeGroup("secret-group", id = "id-secret", attributes = mapOf("public" to listOf("false")))
    val groupResource = mockk<GroupResource>()
    every { groupsResource.group("id-secret") } returns groupResource
    every { groupResource.toRepresentation() } returns privateGroup

    assertThrows<CsmResourceNotFoundException> {
      keycloakClient.getUsersInGroup("id-secret")
    }
  }

  @Test
  fun `getUsersInGroup throws CsmResourceNotFoundException when group has no attributes`() {
    val noAttrGroup = makeGroup("no-attr-group", id = "id-no-attr", attributes = null)
    val groupResource = mockk<GroupResource>()
    every { groupsResource.group("id-no-attr") } returns groupResource
    every { groupResource.toRepresentation() } returns noAttrGroup

    assertThrows<CsmResourceNotFoundException> {
      keycloakClient.getUsersInGroup("id-no-attr")
    }
  }

  @Test
  fun `listRBACMembers returns users and groups present in the ACL`() {
    val publicGroup =
        makeGroup("analysts", id = "id-analysts", attributes = mapOf("public" to listOf("true")))
    val otherPublicGroup =
        makeGroup("other-group", id = "id-other", attributes = mapOf("public" to listOf("true")))
    val aliceUser = makeUser("alice@cosmotech.com")
    val bobUser = makeUser("bob@cosmotech.com")
    val carolUser = makeUser("carol@cosmotech.com")

    // getAllGroups mock
    every { groupsResource.count(true) } returns mapOf("count" to 2L)
    every { groupsResource.groups(any(), any(), any(), any()) } returns
        listOf(publicGroup, otherPublicGroup)

    // getAllUsers mock
    every { usersResource.list() } returns listOf(aliceUser, carolUser)

    // getUsersInGroup for analysts group
    val analystsGroupResource = mockk<GroupResource>()
    every { groupsResource.group("id-analysts") } returns analystsGroupResource
    every { analystsGroupResource.toRepresentation() } returns publicGroup
    every { analystsGroupResource.members() } returns listOf(bobUser)

    val acl =
        listOf(
            RbacAccessControl("alice@cosmotech.com", "admin"),
            RbacAccessControl("analysts", "viewer"),
        )

    val result = keycloakClient.listRBACMembers(acl)

    // alice is in ACL and in getAllUsers → should be in users
    assertEquals(1, result.users.size)
    assertEquals("alice@cosmotech.com", result.users[0].id)
    assertEquals("admin", result.users[0].role)

    // analysts is in ACL and in getAllGroups → should be in groups; other-group is NOT in ACL
    assertEquals(1, result.groups.size)
    assertEquals("analysts", result.groups[0].id)
    assertEquals("viewer", result.groups[0].role)
    assertEquals(listOf("bob@cosmotech.com"), result.groups[0].users)
  }

  @Test
  fun `listRBACMembers returns empty when ACL is empty`() {
    every { groupsResource.count(true) } returns mapOf("count" to 0L)
    every { usersResource.list() } returns emptyList()

    val result = keycloakClient.listRBACMembers(emptyList())

    assertTrue(result.users.isEmpty())
    assertTrue(result.groups.isEmpty())
  }

  @Test
  fun `listRBACMembers excludes users not in ACL`() {
    val aliceUser = makeUser("alice@cosmotech.com")
    val bobUser = makeUser("bob@cosmotech.com")

    every { groupsResource.count(true) } returns mapOf("count" to 0L)
    every { usersResource.list() } returns listOf(aliceUser, bobUser)

    // Only alice is in ACL
    val acl = listOf(RbacAccessControl("alice@cosmotech.com", "editor"))

    val result = keycloakClient.listRBACMembers(acl)

    assertEquals(1, result.users.size)
    assertEquals("alice@cosmotech.com", result.users[0].id)
    assertEquals("editor", result.users[0].role)
  }

  @Test
  fun `listRBACMembers deduplicates users within a group`() {
    val publicGroup =
        makeGroup("analysts", id = "id-analysts", attributes = mapOf("public" to listOf("true")))
    val dupUser = makeUser("dup@cosmotech.com")

    every { groupsResource.count(true) } returns mapOf("count" to 1L)
    every { groupsResource.groups(any(), any(), any(), any()) } returns listOf(publicGroup)
    every { usersResource.list() } returns emptyList()

    val analystsGroupResource = mockk<GroupResource>()
    every { groupsResource.group("id-analysts") } returns analystsGroupResource
    every { analystsGroupResource.toRepresentation() } returns publicGroup
    // Same user appears twice in Keycloak response
    every { analystsGroupResource.members() } returns listOf(dupUser, dupUser)

    val acl = listOf(RbacAccessControl("analysts", "viewer"))

    val result = keycloakClient.listRBACMembers(acl)

    assertEquals(1, result.groups.size)
    assertEquals(1, result.groups[0].users.size)
    assertEquals("dup@cosmotech.com", result.groups[0].users[0])
  }

  @Test
  fun `listKeycloakMembers returns all users and groups with their Keycloak roles`() {
    val publicGroup =
        makeGroup(
            "analysts",
            id = "id-analysts",
            attributes = mapOf("public" to listOf("true")),
            realmRoles = listOf("Organization.User"),
        )
    val aliceUser = makeUser("alice@cosmotech.com", id = "user-alice")
    val bobUser = makeUser("bob@cosmotech.com", id = "user-bob")

    every { groupsResource.count(true) } returns mapOf("count" to 1L)
    every { groupsResource.groups(any(), any(), any(), any()) } returns listOf(publicGroup)
    every { usersResource.list() } returns listOf(aliceUser, bobUser)

    // getUserRoles for alice → Platform.Admin
    val aliceUserResource = mockk<UserResource>()
    val aliceRoleMapping = mockk<RoleMappingResource>()
    val aliceRoleScopeResource = mockk<RoleScopeResource>()
    every { usersResource.get("user-alice") } returns aliceUserResource
    every { aliceUserResource.roles() } returns aliceRoleMapping
    every { aliceRoleMapping.realmLevel() } returns aliceRoleScopeResource
    every { aliceRoleScopeResource.listAll() } returns listOf(makeRole("Platform.Admin"))

    // getUserRoles for bob → Organization.User
    val bobUserResource = mockk<UserResource>()
    val bobRoleMapping = mockk<RoleMappingResource>()
    val bobRoleScopeResource = mockk<RoleScopeResource>()
    every { usersResource.get("user-bob") } returns bobUserResource
    every { bobUserResource.roles() } returns bobRoleMapping
    every { bobRoleMapping.realmLevel() } returns bobRoleScopeResource
    every { bobRoleScopeResource.listAll() } returns listOf(makeRole("Organization.User"))

    // getUsersInGroup for analysts
    val analystsGroupResource = mockk<GroupResource>()
    every { groupsResource.group("id-analysts") } returns analystsGroupResource
    every { analystsGroupResource.toRepresentation() } returns publicGroup
    every { analystsGroupResource.members() } returns listOf(aliceUser)

    val result = keycloakClient.listKeycloakMembers()

    assertEquals(2, result.users.size)
    val alice = result.users.first { it.id == "alice@cosmotech.com" }
    val bob = result.users.first { it.id == "bob@cosmotech.com" }
    assertEquals("Platform.Admin", alice.role)
    assertEquals("Organization.User", bob.role)

    assertEquals(1, result.groups.size)
    assertEquals("analysts", result.groups[0].id)
    assertEquals("Organization.User", result.groups[0].role)
    assertEquals(listOf("alice@cosmotech.com"), result.groups[0].users)
  }

  @Test
  fun `listKeycloakMembers assigns empty role when user has no matching Keycloak roles`() {
    val unknownUser = makeUser("unknown@cosmotech.com", id = "user-unknown")

    every { groupsResource.count(true) } returns mapOf("count" to 0L)
    every { usersResource.list() } returns listOf(unknownUser)

    val unknownUserResource = mockk<UserResource>()
    val unknownRoleMapping = mockk<RoleMappingResource>()
    val unknownRoleScopeResource = mockk<RoleScopeResource>()
    every { usersResource.get("user-unknown") } returns unknownUserResource
    every { unknownUserResource.roles() } returns unknownRoleMapping
    every { unknownRoleMapping.realmLevel() } returns unknownRoleScopeResource
    every { unknownRoleScopeResource.listAll() } returns listOf(makeRole("some-other-role"))

    val result = keycloakClient.listKeycloakMembers()

    assertEquals(1, result.users.size)
    assertEquals("unknown@cosmotech.com", result.users[0].id)
    assertEquals("", result.users[0].role)
  }

  @Test
  fun `listKeycloakMembers assigns empty role when group has no realmRoles`() {
    val noRolesGroup =
        makeGroup(
            "no-roles-group",
            id = "id-no-roles",
            attributes = mapOf("public" to listOf("true")),
            realmRoles = null,
        )

    every { groupsResource.count(true) } returns mapOf("count" to 1L)
    every { groupsResource.groups(any(), any(), any(), any()) } returns listOf(noRolesGroup)
    every { usersResource.list() } returns emptyList()

    val noRolesGroupResource = mockk<GroupResource>()
    every { groupsResource.group("id-no-roles") } returns noRolesGroupResource
    every { noRolesGroupResource.toRepresentation() } returns noRolesGroup
    every { noRolesGroupResource.members() } returns emptyList()

    val result = keycloakClient.listKeycloakMembers()

    assertEquals(1, result.groups.size)
    assertEquals("no-roles-group", result.groups[0].id)
    assertEquals("", result.groups[0].role)
  }

  @Test
  fun `listKeycloakMembers returns empty when no users and no groups`() {
    every { groupsResource.count(true) } returns mapOf("count" to 0L)
    every { usersResource.list() } returns emptyList()

    val result = keycloakClient.listKeycloakMembers()

    assertTrue(result.users.isEmpty())
    assertTrue(result.groups.isEmpty())
  }

  @Test
  fun `listKeycloakMembers paginates when group count exceeds MAX_PAGE_SIZE`() {
    val groups =
        (1..150).map { i ->
          makeGroup(
              "group-$i",
              id = "id-$i",
              attributes = mapOf("public" to listOf("true")),
          )
        }

    every { groupsResource.count(true) } returns mapOf("count" to 150L)
    // First page: groups 0..99, second page: groups 100..149
    every { groupsResource.groups(any(), 0, 100, false) } returns groups.subList(0, 100)
    every { groupsResource.groups(any(), 100, 100, false) } returns groups.subList(100, 150)
    every { usersResource.list() } returns emptyList()

    // Mock getUsersInGroup for each group
    groups.forEach { group ->
      val groupResource = mockk<GroupResource>()
      every { groupsResource.group(group.id) } returns groupResource
      every { groupResource.toRepresentation() } returns group
      every { groupResource.members() } returns emptyList()
    }

    val result = keycloakClient.listKeycloakMembers()

    assertEquals(150, result.groups.size)
  }

  // ---------------------------------------------------------------------------
  // Tests Helpers
  // ---------------------------------------------------------------------------

  private fun makeGroup(
      name: String,
      id: String = "id-$name",
      attributes: Map<String, List<String>>? = null,
      realmRoles: List<String>? = null,
  ): GroupRepresentation {
    val group = GroupRepresentation()
    group.id = id
    group.name = name
    group.attributes = attributes
    group.realmRoles = realmRoles
    return group
  }

  private fun makeUser(username: String, id: String = "id-$username"): UserRepresentation {
    val user = UserRepresentation()
    user.id = id
    user.username = username
    return user
  }

  private fun makeRole(name: String): RoleRepresentation {
    val role = RoleRepresentation()
    role.name = name
    return role
  }
}
