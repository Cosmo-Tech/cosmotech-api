// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.argo

import com.cosmotech.api.argo.ArgoRetrofit
import com.cosmotech.api.argo.WorkflowUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory


class WorkflowUtilsTests {
  private val logger = LoggerFactory.getLogger(WorkflowUtilsTests::class.java)

  val ARGO_SERVER = "https://argo-server:2746"

  @Test
  fun `Constructor return valid object`() {
    val wu = get_workflowUtils()
    assertNotNull(wu)
    assertAll(
      org.junit.jupiter.api.function.Executable { assertNotNull(wu) },
      org.junit.jupiter.api.function.Executable { assertEquals(ARGO_SERVER, wu.baseUrl) }
    )
  }

  private fun get_workflowUtils(): WorkflowUtils {
    return WorkflowUtils(ArgoRetrofit(ARGO_SERVER), ARGO_SERVER)
  }
}
