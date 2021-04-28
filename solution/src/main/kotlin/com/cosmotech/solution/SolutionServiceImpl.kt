// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import org.springframework.stereotype.Service

@Service
class SolutionServiceImpl : AbstractPhoenixService(), SolutionApiService {
  override fun findAllSolutions(organizationId: String): List<Solution> {
    TODO("Not yet implemented")
  }

  override fun findSolutionById(organizationId: String, solutionId: String): Solution {
    TODO("Not yet implemented")
  }

  override fun createSolution(organizationId: String, solution: Solution): Solution {
    TODO("Not yet implemented")
  }

  override fun deleteSolution(organizationId: String, solutionId: String): Solution {
    TODO("Not yet implemented")
  }

  override fun updateSolution(
      organizationId: String,
      solutionId: String,
      solution: Solution
  ): Solution {
    TODO("Not yet implemented")
  }

  override fun upload(
      organizationId: kotlin.String,
      body: org.springframework.core.io.Resource
  ): Solution {
    TODO("Not yet implemented")
  }

  override fun uploadRunTemplateHandler(
      organizationId: kotlin.String,
      solutionId: kotlin.String,
      runTemplateId: kotlin.String,
      handlerId: kotlin.String,
      body: org.springframework.core.io.Resource?
  ): Unit {
    TODO("Not yet implemented")
  }
}
