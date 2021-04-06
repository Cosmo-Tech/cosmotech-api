// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.simulator

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.simulator.api.SimulatorsApiService
import com.cosmotech.simulator.domain.Simulator
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.introspector.BeanAccess

@Service
class SimulatorServiceImpl : AbstractPhoenixService(), SimulatorsApiService {
  override fun findAllSimulators(): List<Simulator> {
    TODO("Not yet implemented")
  }

  override fun findSimulatorById(simulatorId: String): Simulator {
    TODO("Not yet implemented")
  }

  override fun registerSimulator(simulator: Simulator): Simulator {
    TODO("Not yet implemented")
  }

  override fun uploadSimulator(body: org.springframework.core.io.Resource): Simulator {
    val yaml = Yaml()
    yaml.setBeanAccess(BeanAccess.FIELD)
    val simulator = yaml.loadAs(body.getInputStream(), Simulator::class.java)
    return simulator
  }

  override fun unregisterSimulator(simulatorId: String): Simulator {
    TODO("Not yet implemented")
  }
}
