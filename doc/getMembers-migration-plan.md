# Plan: Reproduce `getOrganizationMembers` Pattern Across Modules

## Overview

Replicate the `getOrganizationMembers` endpoint pattern to the **workspace**, **runner**, and **dataset** modules.
Each module requires three steps: OpenAPI modification, Keycloak Kotlin extension functions, and service implementation.

---

## Step 1: OpenAPI YAML Modifications

For each module YAML file, add the following:

### a) New Path Endpoint

| Module    | Path                                                                              | Operation ID          | File Location                                    |
|-----------|-----------------------------------------------------------------------------------|-----------------------|--------------------------------------------------|
| Workspace | `GET /organizations/{organization_id}/workspaces/{workspace_id}/members`          | `getWorkspaceMembers` | `workspace/src/main/openapi/workspace.yaml`      |
| Runner    | `GET /organizations/{organization_id}/runners/{runner_id}/members`                | `getRunnerMembers`    | `runner/src/main/openapi/runner.yaml`            |
| Dataset   | `GET /organizations/{organization_id}/datasets/{dataset_id}/members`              | `getDatasetMembers`   | `dataset/src/main/openapi/dataset.yaml`          |

**Response codes:**
- `200` – Returns the `*Members` schema
- `404` – Resource unknown or inaccessible

### b) New Schemas

Mirror `OrganizationMembers`, `OrganizationMemberUser`, `OrganizationMemberGroup` for each module:

| Module    | Schemas to Add                                                    |
|-----------|-------------------------------------------------------------------|
| Workspace | `WorkspaceMembers`, `WorkspaceMemberUser`, `WorkspaceMemberGroup` |
| Runner    | `RunnerMembers`, `RunnerMemberUser`, `RunnerMemberGroup`          |
| Dataset   | `DatasetMembers`, `DatasetMemberUser`, `DatasetMemberGroup`       |

**Schema structure:**

```yaml
*Members:
  type: object
  properties:
    users:
      type: array
      items: $ref '*MemberUser'
    groups:
      type: array
      items: $ref '*MemberGroup'

*MemberUser:
  type: object
  required: [id, role]
  properties:
    id:
      type: string
    role:
      type: string

*MemberGroup:
  type: object
  required: [id, role, users]
  properties:
    id:
      type: string
    role:
      type: string
    users:
      type: array
      items:
        type: string
```

### c) New Examples

Add a `Brewery*Members` example for each module in the `components.examples` section.

---

## Step 2: Keycloak Kotlin Extension Functions

Instead of dedicated transformer classes, add Kotlin extension functions on `KeycloakMembers`,
`KeycloakMemberUser`, and `KeycloakMemberGroup` — mirroring the existing `toOrganizationMembers()`
pattern defined at the bottom of `OrganizationServiceImpl.kt`.

**Where to add them:** As top-level extension functions at the bottom of each module's service implementation file.

| Module    | Extension Function       | Target File                                                                                  |
|-----------|--------------------------|----------------------------------------------------------------------------------------------|
| Workspace | `toWorkspaceMembers()`   | `workspace/src/main/kotlin/com/cosmotech/workspace/service/WorkspaceServiceImpl.kt`          |
| Runner    | `toRunnerMembers()`      | `runner/src/main/kotlin/com/cosmotech/runner/service/RunnerServiceImpl.kt`                   |
| Dataset   | `toDatasetMembers()`     | `dataset/src/main/kotlin/com/cosmotech/dataset/service/DatasetServiceImpl.kt`                |

**Extension functions to add (same structure for each module):**

```kotlin
// --- Workspace example ---
fun KeycloakMembers.toWorkspaceMembers() =
    WorkspaceMembers(
        users = this.users.map { it.toWorkspaceMemberUser() }.toMutableList(),
        groups = this.groups.map { it.toWorkspaceMemberGroup() }.toMutableList(),
    )

fun KeycloakMemberUser.toWorkspaceMemberUser() =
    WorkspaceMemberUser(id = this.id, role = this.role)

fun KeycloakMemberGroup.toWorkspaceMemberGroup() =
    WorkspaceMemberGroup(id = this.id, role = this.role, users = this.users.toMutableList())
```

> Repeat the same pattern for `Runner` and `Dataset`, replacing the type prefixes accordingly.

**Reference:** The organization module defines `KeycloakMembers.toOrganizationMembers()` at the bottom of
`organization/src/main/kotlin/com/cosmotech/organization/service/OrganizationServiceImpl.kt`.

---

## Step 3: Service Implementation

For each module's service implementation, add a function that calls `keycloak.listCosmotechMembers()`
and maps the result using the extension functions from Step 2.

```kotlin
// Workspace
override fun getWorkspaceMembers(organizationId: String, workspaceId: String): WorkspaceMembers {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    val rbacSecurity = workspace.security.toGenericSecurity(workspaceId)
    return keycloak.listCosmotechMembers(rbacSecurity.accessControlList).toWorkspaceMembers()
}

// Runner
override fun getRunnerMembers(organizationId: String, runnerId: String): RunnerMembers {
    val runner = getVerifiedRunner(organizationId, runnerId, PERMISSION_READ_SECURITY)
    val rbacSecurity = runner.security.toGenericSecurity(runnerId)
    return keycloak.listCosmotechMembers(rbacSecurity.accessControlList).toRunnerMembers()
}

// Dataset
override fun getDatasetMembers(organizationId: String, datasetId: String): DatasetMembers {
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_READ_SECURITY)
    val rbacSecurity = dataset.security.toGenericSecurity(datasetId)
    return keycloak.listCosmotechMembers(rbacSecurity.accessControlList).toDatasetMembers()
}
```

**Reference:** The organization module implements this in `OrganizationServiceImpl.kt`:

```kotlin
override fun getOrganizationMembers(organizationId: String): OrganizationMembers {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    val rbacSecurity = organization.security.toGenericSecurity(organizationId)
    return keycloak.listCosmotechMembers(rbacSecurity.accessControlList).toOrganizationMembers()
}
```

---

## Execution Order Per Module

| # | Task                                            | Target File/Class                                                                              |
|---|-------------------------------------------------|------------------------------------------------------------------------------------------------|
| 1 | Add path + schemas + examples to OpenAPI        | `workspace.yaml` / `runner.yaml` / `dataset.yaml`                                             |
| 2 | Regenerate API interfaces                       | Gradle build                                                                                   |
| 3 | Add `to*Members()` Kotlin extension functions   | Bottom of `WorkspaceServiceImpl.kt` / `RunnerServiceImpl.kt` / `DatasetServiceImpl.kt`        |
| 4 | Implement `get*Members()` service function      | `WorkspaceServiceImpl.kt` / `RunnerServiceImpl.kt` / `DatasetServiceImpl.kt`                  |
| 5 | Add unit tests                                  | `WorkspaceServiceImplTest.kt` / `RunnerServiceImplTest.kt` / `DatasetServiceImplTest.kt`      |

---

## Reference: Organization Implementation

The pattern to follow is the `getOrganizationMembers` endpoint defined in `organization.yaml`:

- **Path:** `GET /organizations/{organization_id}/members`
- **Operation ID:** `getOrganizationMembers`
- **Response Schema:** `OrganizationMembers` (containing `OrganizationMemberUser[]` and `OrganizationMemberGroup[]`)
- **Service file:** `organization/src/main/kotlin/com/cosmotech/organization/service/OrganizationServiceImpl.kt`

### Reference Schemas (`organization.yaml`)

```yaml
OrganizationMembers:
  type: object
  description: The Organization members, including users and groups
  properties:
    users:
      type: array
      items:
        $ref: '#/components/schemas/OrganizationMemberUser'
    groups:
      type: array
      items:
        $ref: '#/components/schemas/OrganizationMemberGroup'

OrganizationMemberUser:
  type: object
  required: [id, role]
  properties:
    id:
      type: string
      description: The user id
    role:
      type: string
      description: The user role in the organization

OrganizationMemberGroup:
  type: object
  required: [id, role, users]
  properties:
    id:
      type: string
      description: The group id
    role:
      type: string
      description: The group role in the organization
    users:
      type: array
      description: The list of users in the group
      items:
        type: string
```

