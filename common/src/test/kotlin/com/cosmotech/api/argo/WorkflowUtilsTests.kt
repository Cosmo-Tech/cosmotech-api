// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.argo

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

private const val ARGO_SERVER = "https://argo-server:2746"

class WorkflowUtilsTests {

  @Test
  fun `Constructor return valid object`() {
    val wu = get_workflowUtils()
    assertNotNull(wu)
    assertAll(
        org.junit.jupiter.api.function.Executable { assertNotNull(wu) },
        org.junit.jupiter.api.function.Executable { assertEquals(ARGO_SERVER, wu.baseUrl) })
  }

  private fun get_workflowUtils(): WorkflowUtils {
    return WorkflowUtils(ArgoRetrofit(ARGO_SERVER), ARGO_SERVER)
  }
}
