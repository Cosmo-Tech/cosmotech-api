// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.iaminfo.service

import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class IAMInfoServiceImplTests {

  @InjectMockKs lateinit var iamInfoApiService: IAMInfoServiceImpl
}
