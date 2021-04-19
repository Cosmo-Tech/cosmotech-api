// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.simulation

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.simulation.api.SimulationApiService
import com.cosmotech.simulation.domain.Simulation
import com.cosmotech.simulation.domain.SimulationBase
import com.cosmotech.simulation.domain.SimulationLogs
import com.cosmotech.simulation.domain.SimulationLogsOptions
import com.cosmotech.simulation.domain.SimulationSearch
import com.cosmotech.simulation.domain.SimulationStartContainers
import com.cosmotech.simulation.domain.SimulationStartScenario
import com.cosmotech.simulation.domain.SimulationStartSolution
import org.springframework.stereotype.Service

@Service
class SimulationServiceImpl : AbstractPhoenixService(), SimulationApiService {

  override fun deleteSimulation(
      organizationId: kotlin.String,
      simulationId: kotlin.String
  ): Simulation {
    TODO("Not yet implemented")
  }

  override fun findSimulationById(
      organizationId: kotlin.String,
      simulationId: kotlin.String
  ): Simulation {
    TODO("Not yet implemented")
  }

  override fun getScenarioSimulation(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String,
      simulationId: kotlin.String
  ): Simulation {
    TODO("Not yet implemented")
  }

  override fun getScenarioSimulationLogs(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String,
      simulationId: kotlin.String
  ): SimulationLogs {
    TODO("Not yet implemented")
  }

  override fun getScenarioSimulations(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String
  ): List<SimulationBase> {
    TODO("Not yet implemented")
  }

  override fun getWorkspaceSimulations(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): List<SimulationBase> {
    TODO("Not yet implemented")
  }

  override fun runScenario(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String
  ): SimulationBase {
    TODO("Not yet implemented")
  }

  override fun searchSimulationLogs(
      organizationId: kotlin.String,
      simulationId: kotlin.String,
      simulationLogsOptions: SimulationLogsOptions
  ): SimulationLogs {
    TODO("Not yet implemented")
  }

  override fun searchSimulations(
      organizationId: kotlin.String,
      simulationSearch: SimulationSearch
  ): List<SimulationBase> {
    TODO("Not yet implemented")
  }

  override fun startSimulationContainers(
      organizationId: kotlin.String,
      simulationStartContainers: SimulationStartContainers
  ): Simulation {
    TODO("Not yet implemented")
  }

  override fun startSimulationScenario(
      organizationId: kotlin.String,
      simulationStartScenario: SimulationStartScenario
  ): Simulation {
    TODO("Not yet implemented")
  }

  override fun startSimulationSolution(
      organizationId: kotlin.String,
      simulationStartSolution: SimulationStartSolution
  ): Simulation {
    TODO("Not yet implemented")
  }
}
