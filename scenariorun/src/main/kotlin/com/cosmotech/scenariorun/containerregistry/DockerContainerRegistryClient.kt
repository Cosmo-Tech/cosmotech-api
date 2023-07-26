// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.containerregistry

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.containerregistry.RegistryClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service("csmDockerContainerRegistry")
@Suppress("UnusedPrivateMember")
@ConditionalOnProperty(
    name = ["csm.platform.containerRegistry.provider"],
    havingValue = "local",
    matchIfMissing = true)
class DockerContainerRegistryClient(private val csmPlatformProperties: CsmPlatformProperties) :
    RegistryClient {

  override fun getEndpoint() = "Not yet implemented"

  override fun checkSolutionImage(repository: String, tag: String) {
    // TODO("Not yet implemented")
  }
}
