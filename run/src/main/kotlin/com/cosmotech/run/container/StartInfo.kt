// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.container

import com.cosmotech.run.domain.ContainerResourceSizeInfo
import com.cosmotech.run.domain.ContainerResourceSizing
import com.cosmotech.run.workflow.RunStartContainers
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerResourceSizing
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateResourceSizing
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import io.kubernetes.client.custom.Quantity

data class StartInfo(
    val startContainers: RunStartContainers,
    val runner: Runner,
    val workspace: Workspace,
    val solution: Solution,
    val runTemplate: RunTemplate,
    val csmSimulationId: String,
)

data class SizingInfo(val cpu: String, val memory: String)

data class Sizing(val requests: SizingInfo, val limits: SizingInfo)

fun Sizing.toContainerResourceSizing(): ContainerResourceSizing {
  return ContainerResourceSizing(
      requests = ContainerResourceSizeInfo(cpu = this.requests.cpu, memory = this.requests.memory),
      limits = ContainerResourceSizeInfo(cpu = this.limits.cpu, memory = this.limits.memory))
}

internal val BASIC_SIZING =
    Sizing(
        requests = SizingInfo(cpu = "3", memory = "4Gi"),
        limits = SizingInfo(cpu = "3", memory = "4Gi"))

internal val HIGH_MEMORY_SIZING =
    Sizing(
        requests = SizingInfo(cpu = "7", memory = "57Gi"),
        limits = SizingInfo(cpu = "7", memory = "57Gi"))

internal val HIGH_CPU_SIZING =
    Sizing(
        requests = SizingInfo(cpu = "70", memory = "130Gi"),
        limits = SizingInfo(cpu = "70", memory = "130Gi"))

fun RunnerResourceSizing.toSizing(): Sizing {
  return Sizing(
      requests = SizingInfo(cpu = this.requests.cpu, memory = this.requests.memory),
      limits = SizingInfo(cpu = this.limits.cpu, memory = this.limits.memory))
}

fun RunTemplateResourceSizing.toSizing(): Sizing {
  return Sizing(
      requests = SizingInfo(cpu = this.requests.cpu, memory = this.requests.memory),
      limits = SizingInfo(cpu = this.limits.cpu, memory = this.limits.memory))
}

fun ContainerResourceSizing.getRequestsMap(): Map<String, Quantity> {
  return mapOf("cpu" to Quantity(this.requests.cpu), "memory" to Quantity(this.requests.memory))
}

fun ContainerResourceSizing.getLimitsMap(): Map<String, Quantity> {
  return mapOf("cpu" to Quantity(this.limits.cpu), "memory" to Quantity(this.limits.memory))
}
