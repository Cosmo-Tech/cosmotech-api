// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.service

import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenario.domain.ScenarioSecurity
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity

class CsmSecurity(
    var defaultRole: String,
    var accessList: MutableList<Pair<String, String>> = mutableListOf()
) {
  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_EDITOR_USER = "test.editor@cosmotech.com"
  val CONNECTED_READER_USER = "test.reader@cosmotech.com"
  val CONNECTED_VIEWER_USER = "test.user@cosmotech.com"
  val CONNECTED_VALIDATOR_USER = "test.validator@cosmotech.com"
  val CONNECTED_NONE_USER = "test.none@cosmotech.com"

  fun addRole(id: String, role: String): CsmSecurity {
    this.accessList.add(Pair(id, role))
    return this
  }

  fun addClassicRole(): CsmSecurity {
    this.accessList.add(Pair(CONNECTED_ADMIN_USER, "admin"))
    this.accessList.add(Pair(CONNECTED_EDITOR_USER, "editor"))
    this.accessList.add(Pair(CONNECTED_READER_USER, "reader"))
    this.accessList.add(Pair(CONNECTED_VIEWER_USER, "viewer"))
    this.accessList.add(Pair(CONNECTED_VALIDATOR_USER, "validator"))
    this.accessList.add(Pair(CONNECTED_NONE_USER, "none"))
    return this
  }

  fun toOrganisationSecurity(): OrganizationSecurity {
    return OrganizationSecurity(
        default = defaultRole,
        accessControlList =
            accessList
                .map { pair -> OrganizationAccessControl(pair.first, pair.second) }
                .toMutableList())
  }

  fun toWorkspaceSecurity(): WorkspaceSecurity {
    return WorkspaceSecurity(
        default = defaultRole,
        accessControlList =
            accessList
                .map { pair -> WorkspaceAccessControl(pair.first, pair.second) }
                .toMutableList())
  }

  fun toScenarioSecurity(): ScenarioSecurity {
    return ScenarioSecurity(
        default = defaultRole,
        accessControlList =
            accessList
                .map { pair -> ScenarioAccessControl(pair.first, pair.second) }
                .toMutableList())
  }
}
