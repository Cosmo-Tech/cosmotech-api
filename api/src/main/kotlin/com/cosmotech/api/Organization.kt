// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.domain.OrganizationServices
import com.fasterxml.jackson.annotation.JsonProperty
import com.redis.om.spring.annotations.Document
import jakarta.validation.Valid
import org.springframework.data.annotation.Id

/**
 * CosmoTech an Organization
 * @param id the Organization unique identifier
 * @param name the Organization name
 * @param ownerId the Owner User Id
 * @param services
 * @param security
 */
@Document
data class Organization(
    @field:JsonProperty("id") @Id var id: kotlin.String? = null,
    @field:JsonProperty("name")
    @com.redis.om.spring.annotations.Searchable
    var name: kotlin.String? = null,
    @field:JsonProperty("ownerId") var ownerId: kotlin.String? = null,
    @field:Valid @field:JsonProperty("services") var services: OrganizationServices? = null,
    @field:Valid
    @field:JsonProperty("security")
    @com.redis.om.spring.annotations.Indexed
    var security: OrganizationSecurity? = null
)
