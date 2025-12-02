// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.security.filters

import com.cosmotech.common.config.CsmPlatformProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.filter.OncePerRequestFilter

class ApiKeyAuthenticationFilter(val csmPlatformProperties: CsmPlatformProperties) :
    OncePerRequestFilter() {

  override fun doFilterInternal(
      request: HttpServletRequest,
      response: HttpServletResponse,
      chain: FilterChain,
  ) {
    logger.debug("API-Key filter starts")
    val allowedApiKeyConsumers = csmPlatformProperties.authorization.allowedApiKeyConsumers

    val matchingApiKeyHeaderRequests =
        allowedApiKeyConsumers.filter { apiKeyConsumer ->
          request.getHeader(apiKeyConsumer.apiKeyHeaderName) != null
        }

    if (matchingApiKeyHeaderRequests.isNotEmpty()) {

      val matchingApiKeyConsumer =
          matchingApiKeyHeaderRequests.firstOrNull { apiKeyConsumer ->
            request.getHeader(apiKeyConsumer.apiKeyHeaderName) == apiKeyConsumer.apiKey
          }

      if (matchingApiKeyConsumer == null) {
        response.status = HttpStatus.FORBIDDEN.value()
        response.writer.write("Wrong value for api API-Key")
        return
      } else {
        val apiKeyHeaderName = matchingApiKeyConsumer.apiKeyHeaderName
        val apiKeyValueConfigured = matchingApiKeyConsumer.apiKey
        val securedUris = matchingApiKeyConsumer.securedUris
        val associatedRole = matchingApiKeyConsumer.associatedRole
        logger.debug("Request matches with API-Key ${matchingApiKeyConsumer.apiKeyHeaderName}")

        if (securedUris.isNotEmpty()) {
          logger.debug("Secured Uris are defined")
          logger.debug("Request URI : ${request.requestURI}")
          val requestUri = request.requestURI
          val isUriMatching =
              securedUris
                  .associateWith { it.split("/.*", ignoreCase = true, limit = 2) }
                  .filter { (securedUri, securedUriSplitted) ->
                    filterSecuredUriWithRequestUri(securedUriSplitted, requestUri, securedUri)
                  }
                  .isNotEmpty()

          if (isUriMatching) {
            logger.debug("Everything is matching, save ApiKeyAuthentication into context")
            val securityContext = SecurityContextHolder.getContext()
            securityContext.authentication =
                ApiKeyAuthentication(
                    apiKeyValueConfigured,
                    apiKeyHeaderName,
                    AuthorityUtils.createAuthorityList(associatedRole),
                )
            HttpSessionSecurityContextRepository().saveContext(securityContext, request, response)
          } else {
            response.status = HttpStatus.FORBIDDEN.value()
            response.writer.write("Access not allowed by API-Key")
            return
          }
        } else {
          response.status = HttpStatus.FORBIDDEN.value()
          response.writer.write("Access not allowed by API-Key")
          return
        }
      }
    }

    chain.doFilter(request, response)
  }

  private fun filterSecuredUriWithRequestUri(
      securedUriSplitted: List<String>,
      requestUri: String,
      securedUri: String,
  ) =
      if (securedUriSplitted.size == 1 && securedUriSplitted[0] == "/") {
        true
      } else {
        val requestUriSplitted = requestUri.split(securedUriSplitted[0])
        if (requestUriSplitted.size < 2) {
          false
        } else {
          val uriSuffix = securedUriSplitted[0] + requestUriSplitted[1]
          securedUri.toRegex().matches(uriSuffix)
        }
      }
}

class ApiKeyAuthentication(
    val apiKey: String,
    val apiKeyName: String,
    authorities: MutableCollection<GrantedAuthority>,
) : AbstractAuthenticationToken(authorities) {

  init {
    this.isAuthenticated = true
  }

  override fun getCredentials(): Any? {
    return null
  }

  override fun getPrincipal(): Any {
    return apiKeyName
  }
}
