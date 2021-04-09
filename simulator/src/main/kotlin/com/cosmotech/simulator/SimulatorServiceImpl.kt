// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.simulator

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.simulator.api.SimulatorApiService
import com.cosmotech.simulator.domain.Simulator
import org.springframework.stereotype.Service

@Service
class SimulatorServiceImpl : AbstractPhoenixService(), SimulatorApiService {
  override fun findAllSimulators(organizationId: String): List<Simulator> {
    TODO("Not yet implemented")
  }

  override fun findSimulatorById(organizationId: String, simulatorId: String): Simulator {
    TODO("Not yet implemented")
  }

  override fun createSimulator(organizationId: String, simulator: Simulator): Simulator {
    TODO("Not yet implemented")
  }

  override fun deleteSimulator(organizationId: String, simulatorId: String): Simulator {
    TODO("Not yet implemented")
  }

  override fun updateSimulator(
      organizationId: String,
      simulatorId: String,
      simulator: Simulator
  ): Simulator {
    TODO("Not yet implemented")
  }

  override fun upload(
      organizationId: kotlin.String,
      body: org.springframework.core.io.Resource
  ): Simulator {
    TODO("Not yet implemented")
  }
}
