// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

class ConnectorRemoved(publisher: Any, val connectorId: String) : CsmEvent(publisher)

class ConnectorRemovedForOrganization(
    publisher: Any,
    val organizationId: String,
    val connectorId: String
) : CsmEvent(publisher)
