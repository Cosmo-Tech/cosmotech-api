// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.meta.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.MockKAnnotations
import io.mockk.mockkStatic
import io.mockk.junit5.MockKExtension
import kotlin.test.assertEquals
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import com.cosmotech.api.utils.getAboutInfo
import com.cosmotech.meta.domain.AboutInfoVersion

@ExtendWith(MockKExtension::class)
class MetaServiceImplTests {

  @InjectMockKs lateinit var metaApiService: MetaServiceImpl

  @BeforeEach
  fun beforeEach() {
    mockkStatic("com.cosmotech.api.utils.AboutInfoKt")
  }

  @TestFactory
  fun `about version info`() =
    mapOf(
      "Release, no label" to AboutInfoVersion(
        major = 1, minor = 2, patch = 3, label = "", build = "abcdef12", release = "", full = ""),
      "Release, no label, no build" to AboutInfoVersion(
        major = 1, minor = 2, patch = 3, label = "", build = "", release = "", full = ""),
      "SNAPSHOT label" to AboutInfoVersion(
        major = 1, minor = 2, patch = 3, label = "SNAPSHOT", build = "abcdef12", release = "", full = ""),
      "'-' in label" to AboutInfoVersion(
        major = 1, minor = 2, patch = 3, label = "my-branch-SNAPSHOT", build = "abcdef12", release = "", full = ""),
    ).map {
      (name, data) -> DynamicTest.dynamicTest("about version info: $name") {
        data.release = "${data.major}.${data.minor}.${data.patch}"
        if (!data.label.isEmpty()){
          data.release += "-${data.label}"
        }
        data.full = data.release + "-${data.build}"

        every { getAboutInfo() } returns JSONObject(mapOf("version" to mapOf(
          "full" to data.full,
          "release" to data.release,
          "build" to data.build)))

        assertEquals(data, metaApiService.about().version)
    }
  }
}
