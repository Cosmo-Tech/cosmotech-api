// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.platform

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.platform.api.PlatformApiService
import com.cosmotech.platform.domain.Platform
import org.springframework.stereotype.Service

@Service
internal class PlatformServiceImpl : AbstractPhoenixService(), PlatformApiService {
  override fun createPlatform(platform: Platform): Platform {
    TODO("Not yet implemented")
  }

  override fun getPlatform(): Platform {
    TODO("Not yet implemented")
  }

  override fun updatePlatform(platform: Platform): Platform {
    TODO("Not yet implemented")
  }
}
