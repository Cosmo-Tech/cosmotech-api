// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.containerregistry

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.exceptions.CsmClientException
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import java.net.URI
import java.util.Base64
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Service("csmContainerRegistry")
class ContainerRegistryService(private val csmPlatformProperties: CsmPlatformProperties) {

  private val logger = LoggerFactory.getLogger(this::class.java)

  private val baseUrl =
      "${csmPlatformProperties.containerRegistry.scheme}://${csmPlatformProperties.containerRegistry.host}"

  private val restClient = RestClient.builder().baseUrl(baseUrl).build()

  // Getting a blob from ACR using the default http client does not work because the client forwards
  // the Authorization header to the redirect query:
  // - the /v2/{repo}/blobs/{digest} endpoint is implemented by ACR with a 307 Temporary Redirect to
  //   a blob storage
  // - the 'Location' header in the response contains the necessary authentication string
  // - BUT the http client, by default, also forwards the initial ACR 'Authorization' header, which
  //   the Azure Blob Storage rejects (rightfully) because this is an ACR auth
  // So for this specific query we use client with redirection disabled and make the follow up query
  // manually without the 'Authorization' header if we need to.
  private val noRedirectClient =
      RestClient.builder()
          .baseUrl(baseUrl)
          .requestFactory(
              HttpComponentsClientHttpRequestFactory(
                  HttpClientBuilder.create().disableRedirectHandling().build()
              )
          )
          .build()

  private fun getHeaderAuthorization(): String {
    val user = csmPlatformProperties.containerRegistry.username!!
    val password = csmPlatformProperties.containerRegistry.password!!
    val basicToken = Base64.getEncoder().encodeToString("$user:$password".toByteArray())
    return "Basic $basicToken"
  }

  fun checkSolutionImage(repository: String, tag: String) {
    try {
      val images =
          restClient
              .get()
              .uri("/v2/$repository/tags/list")
              .header(HttpHeaders.AUTHORIZATION, getHeaderAuthorization())
              .retrieve()
              .body(String::class.java)!!

      val tags: JSONArray = JSONObject(images).get("tags") as JSONArray
      if (!tags.contains(tag)) {
        throw CsmResourceNotFoundException("Solution docker image $repository:$tag not found")
      }
    } catch (e: RestClientException) {
      throw CsmClientException(
          "Solution docker image $repository:$tag check error: ${e.message}",
          e,
      )
    }
  }

  fun getImageLabel(repository: String, tag: String, label: String): String? {
    try {
      val manifest =
          restClient
              .get()
              .uri("/v2/$repository/manifests/$tag")
              .header(HttpHeaders.AUTHORIZATION, getHeaderAuthorization())
              .header(HttpHeaders.ACCEPT, "application/vnd.docker.distribution.manifest.v2+json")
              .retrieve()
              .body(String::class.java)!!

      val digest = JSONObject(manifest).getJSONObject("config").getString("digest")

      val blobResponse =
          noRedirectClient
              .get()
              .uri("/v2/$repository/blobs/$digest")
              .header(HttpHeaders.AUTHORIZATION, getHeaderAuthorization())
              .retrieve()
              .toEntity(String::class.java)

      // If we need to follow a redirect, do it without the initial 'Authorization' header or Azure
      // Blob Storage will complain
      var blob =
          if (blobResponse.statusCode.is3xxRedirection())
              restClient
                  .get()
                  .uri(URI(blobResponse.headers.getFirst(HttpHeaders.LOCATION)))
                  .retrieve()
                  .body(String::class.java)!!
          else blobResponse.body!!

      return JSONObject(blob)
          .getJSONObject("config")
          .optJSONObject("Labels", JSONObject())
          .optString(label, null)
    } catch (e: RestClientException) {
      logger.error("Failed to get label '$label' on docker image '$repository:$tag'", e)
      return null
    }
  }
}
