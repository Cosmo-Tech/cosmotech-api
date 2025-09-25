// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.security

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.security.filters.ApiKeyAuthenticationFilter
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.context.DelegatingSecurityContextRepository
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher
import org.springframework.web.cors.CorsConfiguration

// Business roles
const val ROLE_PLATFORM_ADMIN = "Platform.Admin"
const val ROLE_ORGANIZATION_USER = "Organization.User"
const val ROLE_ORGANIZATION_VIEWER = "Organization.Viewer"

// Allowed read scopes
const val SCOPE_ORGANIZATION_READ = "SCOPE_csm.organization.read"
const val SCOPE_DATASET_READ = "SCOPE_csm.dataset.read"
const val SCOPE_SOLUTION_READ = "SCOPE_csm.solution.read"
const val SCOPE_WORKSPACE_READ = "SCOPE_csm.workspace.read"
const val SCOPE_RUN_READ = "SCOPE_csm.run.read"
const val SCOPE_RUNNER_READ = "SCOPE_csm.runner.read"

// Allowed write scopes
const val SCOPE_ORGANIZATION_WRITE = "SCOPE_csm.organization.write"
const val SCOPE_DATASET_WRITE = "SCOPE_csm.dataset.write"
const val SCOPE_SOLUTION_WRITE = "SCOPE_csm.solution.write"
const val SCOPE_WORKSPACE_WRITE = "SCOPE_csm.workspace.write"
const val SCOPE_RUN_WRITE = "SCOPE_csm.run.write"
const val SCOPE_RUNNER_WRITE = "SCOPE_csm.runner.write"
// Path Datasets
val PATHS_DATASETS =
    listOf(
        "/organizations/*/workspaces/*/datasets",
        "/organizations/*/workspaces/*/datasets/search",
        "/organizations/*/workspaces/*/datasets/*",
        "/organizations/*/workspaces/*/datasets/*/security",
        "/organizations/*/workspaces/*/datasets/*/security/access",
        "/organizations/*/workspaces/*/datasets/*/security/access/*",
        "/organizations/*/workspaces/*/datasets/*/security/default",
        "/organizations/*/workspaces/*/datasets/*/security/users",
        "/organizations/*/workspaces/*/datasets/*/parts",
        "/organizations/*/workspaces/*/datasets/*/parts/search",
        "/organizations/*/workspaces/*/datasets/*/parts/*",
        "/organizations/*/workspaces/*/datasets/*/parts/*/download",
        "/organizations/*/workspaces/*/datasets/*/parts/*/query",
    )

// Path Organizations
val PATHS_ORGANIZATIONS =
    listOf(
        "/organizations",
        "/organizations/permissions",
        "/organizations/*",
        "/organizations/*/permissions/*",
        "/organizations/*/security",
        "/organizations/*/security/access",
        "/organizations/*/security/access/*",
        "/organizations/*/security/default",
        "/organizations/*/security/users")

// Path Runs
val PATHS_RUNS =
    listOf(
        "/organizations/*/workspaces/*/runners/*/runs",
        "/organizations/*/workspaces/*/runners/*/runs/*",
        "/organizations/*/workspaces/*/runners/*/runs/*/data/query",
        "/organizations/*/workspaces/*/runners/*/runs/*/data/send",
        "/organizations/*/workspaces/*/runners/*/runs/*/logs",
        "/organizations/*/workspaces/*/runners/*/runs/*/status")

// Path Runners
val PATHS_RUNNERS =
    listOf(
        "/organizations/*/workspaces/*/runners",
        "/organizations/*/workspaces/*/runners/*",
        "/organizations/*/workspaces/*/runners/*/permissions/*",
        "/organizations/*/workspaces/*/runners/*/security",
        "/organizations/*/workspaces/*/runners/*/security/access",
        "/organizations/*/workspaces/*/runners/*/security/access/*",
        "/organizations/*/workspaces/*/runners/*/security/default",
        "/organizations/*/workspaces/*/runners/*/security/users",
        "/organizations/*/workspaces/*/runners/*/start",
        "/organizations/*/workspaces/*/runners/*/stop")

// Path Solutions
val PATHS_SOLUTIONS =
    listOf(
        "/organizations/*/solutions",
        "/organizations/*/solutions/*",
        "/organizations/*/solutions/*/parameterGroups",
        "/organizations/*/solutions/*/parameterGroups/*",
        "/organizations/*/solutions/*/parameters",
        "/organizations/*/solutions/*/parameters/*",
        "/organizations/*/solutions/*/runTemplates",
        "/organizations/*/solutions/*/runTemplates/*",
        "/organizations/*/solutions/*/security",
        "/organizations/*/solutions/*/security/access",
        "/organizations/*/solutions/*/security/access/*",
        "/organizations/*/solutions/*/security/default",
        "/organizations/*/solutions/*/security/users",
    )

// Path Workspaces files
val PATHS_WORKSPACES_FILES =
    listOf(
        "/organizations/*/workspaces/*/files",
        "/organizations/*/workspaces/*/files/delete",
        "/organizations/*/workspaces/*/files/download")

// Path Workspaces
val PATHS_WORKSPACES =
    listOf(
        "/organizations/*/workspaces",
        "/organizations/*/workspaces/*",
        "/organizations/*/workspaces/*/permissions/*",
        "/organizations/*/workspaces/*/security",
        "/organizations/*/workspaces/*/security/access",
        "/organizations/*/workspaces/*/security/access/*",
        "/organizations/*/workspaces/*/security/default",
        "/organizations/*/workspaces/*/security/users",
    )

// Endpoints roles
val endpointSecurityPublic =
    listOf(
        "/actuator/prometheus",
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
    customOrganizationAdmin: String,
    customOrganizationUser: String,
    customOrganizationViewer: String
) =
    listOf(
        CsmSecurityEndpointsRolesReader(
            paths = listOf("/about"),
            roles =
                arrayOf(
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    customOrganizationUser,
                    customOrganizationViewer),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_DATASETS,
            roles =
                arrayOf(
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    SCOPE_DATASET_READ,
                    SCOPE_DATASET_WRITE,
                    customOrganizationUser,
                    customOrganizationViewer),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_ORGANIZATIONS,
            roles =
                arrayOf(
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    SCOPE_ORGANIZATION_READ,
                    SCOPE_ORGANIZATION_WRITE,
                    customOrganizationUser,
                    customOrganizationViewer),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_SOLUTIONS,
            roles =
                arrayOf(
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    SCOPE_SOLUTION_READ,
                    SCOPE_SOLUTION_WRITE,
                    customOrganizationUser,
                    customOrganizationViewer),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_WORKSPACES,
            roles =
                arrayOf(
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    SCOPE_WORKSPACE_READ,
                    SCOPE_WORKSPACE_WRITE,
                    customOrganizationUser,
                    customOrganizationViewer),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_RUNS,
            roles =
                arrayOf(
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    SCOPE_RUN_READ,
                    SCOPE_RUN_WRITE,
                    customOrganizationUser,
                    customOrganizationViewer),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesReader(
            paths = PATHS_RUNNERS,
            roles =
                arrayOf(
                    ROLE_ORGANIZATION_USER,
                    ROLE_ORGANIZATION_VIEWER,
                    SCOPE_RUNNER_READ,
                    SCOPE_RUNNER_WRITE,
                    customOrganizationUser,
                    customOrganizationViewer),
            customAdmin = customOrganizationAdmin))

@Suppress("LongMethod")
internal fun endpointSecurityWriters(
    customOrganizationAdmin: String,
    customOrganizationUser: String
) =
    listOf(
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_DATASETS,
            roles = arrayOf(ROLE_ORGANIZATION_USER, SCOPE_DATASET_WRITE, customOrganizationUser),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_ORGANIZATIONS,
            roles = arrayOf(SCOPE_ORGANIZATION_WRITE),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_SOLUTIONS,
            roles = arrayOf(ROLE_ORGANIZATION_USER, SCOPE_SOLUTION_WRITE, customOrganizationUser),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_WORKSPACES,
            roles = arrayOf(SCOPE_WORKSPACE_WRITE),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_RUNS,
            roles = arrayOf(ROLE_ORGANIZATION_USER, SCOPE_RUN_WRITE, customOrganizationUser),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_RUNNERS,
            roles = arrayOf(ROLE_ORGANIZATION_USER, SCOPE_RUNNER_WRITE, customOrganizationUser),
            customAdmin = customOrganizationAdmin),
        CsmSecurityEndpointsRolesWriter(
            paths = PATHS_WORKSPACES_FILES,
            roles = arrayOf(ROLE_ORGANIZATION_USER, SCOPE_WORKSPACE_WRITE, customOrganizationUser),
            customAdmin = customOrganizationAdmin),
    )

abstract class AbstractSecurityConfiguration {

  fun getOAuth2ResourceServer(
      http: HttpSecurity,
      organizationAdminGroup: String,
      organizationUserGroup: String,
      organizationViewerGroup: String,
      csmPlatformProperties: CsmPlatformProperties
  ): HttpSecurity {

    val corsHttpMethodsAllowed =
        HttpMethod.values().filterNot { it == HttpMethod.TRACE }.map(HttpMethod::name)

    return http
        .cors { cors ->
          cors.configurationSource {
            val corsConfig = CorsConfiguration().applyPermitDefaultValues()
            corsConfig.apply { allowedMethods = corsHttpMethodsAllowed }
            corsConfig
          }
        }
        .csrf { csrfConfigurer ->
          csmPlatformProperties.authorization.allowedApiKeyConsumers.forEach { apiKeyConsumer ->
            csrfConfigurer.ignoringRequestMatchers(
                RequestHeaderRequestMatcher(apiKeyConsumer.apiKeyHeaderName))
          }
        }
        .securityContext {
          it.securityContextRepository(
              DelegatingSecurityContextRepository(
                  RequestAttributeSecurityContextRepository(),
                  HttpSessionSecurityContextRepository()))
        }
        .addFilterBefore(
            ApiKeyAuthenticationFilter(csmPlatformProperties), AuthorizationFilter::class.java)
        .authorizeHttpRequests { requests ->
          requests
              .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.OPTIONS, "/**"))
              .permitAll()
          // Public paths
          endpointSecurityPublic.forEach { path ->
            requests
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, path))
                .permitAll()
          }
          // Endpoint security for reader roles
          endpointSecurityReaders(
                  organizationAdminGroup, organizationUserGroup, organizationViewerGroup)
              .forEach { endpointsRoles -> endpointsRoles.applyRoles(requests) }

          // Endpoint security for writer roles
          endpointSecurityWriters(organizationAdminGroup, organizationUserGroup).forEach {
              endpointsRoles ->
            endpointsRoles.applyRoles(requests)
          }

          requests.anyRequest().authenticated()
        }
  }
}

internal class CsmSecurityEndpointsRolesWriter(
    val customAdmin: String,
    val paths: List<String>,
    val roles: Array<String>,
) {

  @Suppress("SpreadOperator")
  fun applyRoles(
      requests:
          AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
  ) {
    val authoritiesList = addAdminRolesIfNotAlreadyDefined(this.roles)
    this.paths.forEach { path ->
      requests
          .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.POST, path))
          .hasAnyAuthority(*authoritiesList.toTypedArray())
          .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.PATCH, path))
          .hasAnyAuthority(*authoritiesList.toTypedArray())
          .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.DELETE, path))
          .hasAnyAuthority(*authoritiesList.toTypedArray())
    }
  }

  private fun addAdminRolesIfNotAlreadyDefined(roles: Array<String>): MutableList<String> {
    val authoritiesList = roles.toSet().toMutableList()
    if (ROLE_PLATFORM_ADMIN !in authoritiesList) {
      authoritiesList.add(ROLE_PLATFORM_ADMIN)
    }
    if (customAdmin !in authoritiesList) {
      authoritiesList.add(customAdmin)
    }
    return authoritiesList
  }
}

internal class CsmSecurityEndpointsRolesReader(
    val customAdmin: String,
    val paths: List<String>,
    val roles: Array<String>,
) {

  @Suppress("SpreadOperator")
  fun applyRoles(
      requests:
          AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
  ) {
    val authoritiesList = addAdminRolesIfNotAlreadyDefined(this.roles)
    this.paths.forEach { path ->
      requests
          .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, path))
          .hasAnyAuthority(*authoritiesList.toTypedArray())
    }
  }

  private fun addAdminRolesIfNotAlreadyDefined(roles: Array<String>): MutableList<String> {
    val authoritiesList = roles.toSet().toMutableList()
    if (ROLE_PLATFORM_ADMIN !in authoritiesList) {
      authoritiesList.add(ROLE_PLATFORM_ADMIN)
    }
    if (customAdmin !in authoritiesList) {
      authoritiesList.add(customAdmin)
    }
    return authoritiesList
  }
}
