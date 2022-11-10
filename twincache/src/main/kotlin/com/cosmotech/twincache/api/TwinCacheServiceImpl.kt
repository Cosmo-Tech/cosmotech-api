// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twincache.api

import org.springframework.stereotype.Service

@Service
class TwinCacheServiceImpl : TwincacheApiService {

  override fun getHelloWorld(): List<String> {
    return listOf("This", "is", "a", "helloworld")
  }
}
