// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.security

import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.web.cors.CorsConfiguration

// Business roles
const val ROLE_PLATFORM_ADMIN = "Platform.Admin"
const val ROLE_CONNECTOR_DEVELOPER = "Connector.Developer"
const val ROLE_ORGANIZATION_ADMIN = "Organization.Admin"
const val ROLE_ORGANIZATION_COLLABORATOR = "Organization.Collaborator"
const val ROLE_ORGANIZATION_MODELER = "Organization.Modeler"
const val ROLE_ORGANIZATION_USER = "Organization.User"
const val ROLE_ORGANIZATION_VIEWER = "Organization.Viewer"

// Endpoints roles
const val ROLE_CONNECTOR_READER = "Connector.Reader"
const val ROLE_CONNECTOR_WRITER = "Connector.Writer"
const val ROLE_DATASET_READER = "Dataset.Reader"
const val ROLE_DATASET_WRITER = "Dataset.Writer"
const val ROLE_ORGANIZATION_READER = "Organization.Reader"
const val ROLE_ORGANIZATION_WRITER = "Organization.Writer"
const val ROLE_SCENARIO_READER = "Scenario.Reader"
const val ROLE_SCENARIO_WRITER = "Scenario.Writer"
const val ROLE_SCENARIORUN_READER = "ScenarioRun.Reader"
const val ROLE_SCENARIORUN_WRITER = "ScenarioRun.Writer"
const val ROLE_SOLUTION_READER = "Solution.Reader"
const val ROLE_SOLUTION_WRITER = "Solution.Writer"
const val ROLE_WORKSPACE_READER = "Workspace.Reader"
const val ROLE_WORKSPACE_WRITER = "Workspace.Writer"

// Allowed scopes
const val SCOPE_SCENARIO_READ = "SCOPE_csm.scenario.read"

// Endpoints paths
const val PATH_CONNECTORS = "/connectors"
const val PATH_DATASETS = "/organizations/*/datasets"
const val PATH_ORGANIZATIONS = "/organizations"
const val PATH_ORGANIZATIONS_USERS = "/organizations/*/users"
const val PATH_ORGANIZATIONS_SERVICES = "/organizations/*/services"
val PATHS_ORGANIZATIONS =
    listOf(PATH_ORGANIZATIONS, PATH_ORGANIZATIONS_USERS, PATH_ORGANIZATIONS_SERVICES)
const val PATH_SCENARIOS = "/organizations/*/workspaces/*/scenarios"
const val PATH_SCENARIOS_COMPARE = "/organizations/*/workspaces/*/scenarios/*/compare"
const val PATH_SCENARIOS_USERS = "/organizations/*/workspaces/*/scenarios/*/users"
const val PATH_SCENARIOS_PARAMETERVALUES =
    "/organizations/*/workspaces/*/scenarios/*/parameterValues"
val PATHS_SCENARIOS =
    listOf(
        PATH_SCENARIOS,
        PATH_SCENARIOS_COMPARE,
        PATH_SCENARIOS_USERS,
        PATH_SCENARIOS_PARAMETERVALUES)
const val PATH_SCENARIORUNS = "/organizations/*/scenarioruns"
const val PATH_SCENARIORUNS_STATUS = "/organizations/*/scenarioruns/*/status"
const val PATH_SCENARIORUNS_LOGS = "/organizations/*/scenarioruns/*/logs"
const val PATH_SCENARIORUNS_CUMULATEDLOGS = "/organizations/*/scenarioruns/*/cumulatedlogs"
const val PATH_SCENARIORUNS_WORKSPACES = "/organizations/*/workspaces/scenarioruns"
const val PATH_SCENARIORUNS_SCENARIOS = "/organizations/*/workspaces/*/scenarios/*/scenarioruns"
const val PATH_SCENARIORUNS_SCENARIOS_RUN = "/organizations/*/workspaces/*/scenarios/*/run"
val PATHS_SCENARIORUNS =
    listOf(
        PATH_SCENARIORUNS,
        PATH_SCENARIORUNS_STATUS,
        PATH_SCENARIORUNS_LOGS,
        PATH_SCENARIORUNS_CUMULATEDLOGS,
        PATH_SCENARIORUNS_WORKSPACES,
        PATH_SCENARIORUNS_SCENARIOS)
const val PATH_SOLUTIONS = "/organizations/*/solutions"
const val PATH_SOLUTIONS_PARAMETERS = "/organizations/*/solutions/*/parameters"
const val PATH_SOLUTIONS_PARAMETERGROUPS = "/organizations/*/solutions/*/parameterGroups"
const val PATH_SOLUTIONS_RUNTEMPLATES = "/organizations/*/solutions/*/runTemplates"
const val PATH_SOLUTIONS_RUNTEMPLATES_HANDLERS_UPLOAD =
    "/organizations/*/solutions/*/runTemplates/*/handlers/*/upload"
val PATHS_SOLUTIONS =
    listOf(
        PATH_SOLUTIONS,
        PATH_SOLUTIONS_PARAMETERS,
        PATH_SOLUTIONS_PARAMETERGROUPS,
        PATH_SOLUTIONS_RUNTEMPLATES,
        PATH_SOLUTIONS_RUNTEMPLATES_HANDLERS_UPLOAD)
const val PATH_WORKSPACES = "/organizations/*/workspaces"
const val PATH_WORKSPACES_USERS = "/organizations/*/workspaces/*/users"
val PATHS_WORKSPACES = listOf(PATH_WORKSPACES, PATH_WORKSPACES_USERS)
const val PATH_WORKSPACES_FILES = "/organizations/*/workspaces/*/files"

// Endpoints roles
val endpointSecurityPublic =
    listOf(
        "/actuator/health/**",
        "/actuator/info",
        "/",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/openapi.*",
        "/openapi/*",
        "/openapi",
        "/error",
    )

@Suppress("LongMethod")
internal fun endpointSecurityReaders(
    customOrganizationUser: String,
    customOrganizationViewer: String
) =
    listOf(
        CsmSecurityEndpointsRolesReader(
            paths = listOf(PATH_CONNECTORS),
            roles =
                arrayOf(
                    ROLE_CONNECTOR_READER,
                    ROLE_CONNECTOR_WRITER,
                    ROLE_CONNECTOR_DEVELOPER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    customOrganizationUser,
                    customOrganizationViewer)),
        CsmSecurityEndpointsRolesReader(
            paths = listOf(PATH_DATASETS),
            roles =
                arrayOf(
                    ROLE_DATASET_READER,
                    ROLE_DATASET_WRITER,
                    ROLE_CONNECTOR_DEVELOPER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    customOrganizationUser,
                    customOrganizationViewer)),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_ORGANIZATIONS,
            roles =
                arrayOf(
                    ROLE_ORGANIZATION_READER,
                    ROLE_ORGANIZATION_WRITER,
                    ROLE_CONNECTOR_DEVELOPER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    customOrganizationUser,
                    customOrganizationViewer)),
        CsmSecurityEndpointsRolesReader(
            paths = listOf(PATH_SCENARIOS),
            roles =
                arrayOf(
                    ROLE_SCENARIO_READER,
                    ROLE_SCENARIO_WRITER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    SCOPE_SCENARIO_READ,
                    customOrganizationUser,
                    customOrganizationViewer)),
        CsmSecurityEndpointsRolesReader(
            paths =
                listOf(
                    PATH_SCENARIOS_COMPARE, PATH_SCENARIOS_USERS, PATH_SCENARIOS_PARAMETERVALUES),
            roles =
                arrayOf(
                    ROLE_SCENARIO_READER,
                    ROLE_SCENARIO_WRITER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    customOrganizationUser,
                    customOrganizationViewer)),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_SCENARIORUNS,
            roles =
                arrayOf(
                    ROLE_SCENARIORUN_READER,
                    ROLE_SCENARIORUN_WRITER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    customOrganizationUser)),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_SOLUTIONS,
            roles =
                arrayOf(
                    ROLE_SOLUTION_READER,
                    ROLE_SOLUTION_WRITER,
                    ROLE_CONNECTOR_DEVELOPER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    customOrganizationUser,
                    customOrganizationViewer)),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_WORKSPACES,
            roles =
                arrayOf(
                    ROLE_WORKSPACE_READER,
                    ROLE_WORKSPACE_WRITER,
                    ROLE_CONNECTOR_DEVELOPER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    customOrganizationUser,
                    customOrganizationViewer)),
    )

@Suppress("LongMethod")
internal fun endpointSecurityWriters(customOrganizationUser: String) =
    listOf(
        CsmSecurityEndpointsRolesWriter(
            paths = listOf(PATH_CONNECTORS),
            roles = arrayOf(ROLE_CONNECTOR_WRITER, ROLE_CONNECTOR_DEVELOPER)),
        CsmSecurityEndpointsRolesWriter(
            paths = listOf(PATH_DATASETS),
            roles =
                arrayOf(
                    ROLE_DATASET_WRITER,
                    ROLE_CONNECTOR_DEVELOPER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    customOrganizationUser)),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_ORGANIZATIONS,
            roles =
                arrayOf(
                    ROLE_ORGANIZATION_WRITER,
                    ROLE_ORGANIZATION_ADMIN,
                )),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_SCENARIOS,
            roles =
                arrayOf(
                    ROLE_SCENARIO_WRITER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    customOrganizationUser)),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_SCENARIORUNS,
            roles =
                arrayOf(
                    ROLE_SCENARIORUN_WRITER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    customOrganizationUser)),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_SOLUTIONS,
            roles =
                arrayOf(
                    ROLE_SOLUTION_WRITER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                )),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_WORKSPACES,
            roles =
                arrayOf(
                    ROLE_WORKSPACE_WRITER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                )),
        CsmSecurityEndpointsRolesWriter(
            paths = listOf(PATH_WORKSPACES_FILES),
            roles =
                arrayOf(
                    ROLE_WORKSPACE_WRITER,
                    ROLE_ORGANIZATION_ADMIN,
                    ROLE_ORGANIZATION_COLLABORATOR,
                    ROLE_ORGANIZATION_MODELER,
                    ROLE_ORGANIZATION_USER,
                    customOrganizationUser)),
    )

abstract class AbstractSecurityConfiguration : WebSecurityConfigurerAdapter() {

  fun getOAuth2JwtConfigurer(
      http: HttpSecurity,
      organizationUserGroup: String,
      organizationViewerGroup: String
  ): OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer? {

    val corsHttpMethodsAllowed =
        HttpMethod.values().filterNot { it == HttpMethod.TRACE }.map(HttpMethod::name)

    return http.cors()
        .configurationSource {
          CorsConfiguration().applyPermitDefaultValues().apply {
            allowedMethods = corsHttpMethodsAllowed
          }
        }
        .and()
        .authorizeRequests { requests ->
          requests.antMatchers(HttpMethod.OPTIONS, "/**").permitAll()

          // Public paths
          endpointSecurityPublic.forEach { path ->
            requests.antMatchers(HttpMethod.GET, path).permitAll()
          }

          // Endpoint security for reader roles
          endpointSecurityReaders(organizationUserGroup, organizationViewerGroup).forEach {
              endpointsRoles ->
            endpointsRoles.applyRoles(requests)
          }

          // Endpoint security for writer roles
          endpointSecurityWriters(organizationUserGroup).forEach { endpointsRoles ->
            endpointsRoles.applyRoles(requests)
          }

          requests.anyRequest().authenticated()
        }
        .oauth2ResourceServer()
        .jwt()
  }
}

internal class CsmSecurityEndpointsRolesWriter(
    val paths: List<String>,
    val roles: Array<String>,
) {

  @Suppress("SpreadOperator")
  fun applyRoles(
      requests: ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry
  ) {
    this.paths.forEach { path ->
      requests
          .antMatchers(HttpMethod.POST, path, "${path}/*")
          .hasAnyAuthority(ROLE_PLATFORM_ADMIN, *this.roles)
          .antMatchers(HttpMethod.PATCH, path, "${path}/*")
          .hasAnyAuthority(ROLE_PLATFORM_ADMIN, *this.roles)
          .antMatchers(HttpMethod.DELETE, path, "${path}/*")
          .hasAnyAuthority(ROLE_PLATFORM_ADMIN, *this.roles)
    }
  }
}

internal class CsmSecurityEndpointsRolesReader(
    val paths: List<String>,
    val roles: Array<String>,
) {

  @Suppress("SpreadOperator")
  fun applyRoles(
      requests: ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry
  ) {
    this.paths.forEach { path ->
      requests
          .antMatchers(HttpMethod.GET, path, "${path}/*")
          .hasAnyAuthority(ROLE_PLATFORM_ADMIN, *this.roles)
    }
  }
}

class CsmJwtClaimValueInCollectionValidator(
    claimName: String,
    private val allowed: Collection<String>
) : OAuth2TokenValidator<Jwt> {

  private val jwtClaimValidator: JwtClaimValidator<String> =
      JwtClaimValidator(claimName, allowed::contains)

  override fun validate(token: Jwt?): OAuth2TokenValidatorResult =
      this.jwtClaimValidator.validate(
          token ?: throw IllegalArgumentException("JWT must not be null"))
}
