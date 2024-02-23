package com.cosmotech.runner

import com.cosmotech.api.events.CsmEvent
import com.cosmotech.api.events.CsmRequestResponseEvent
import org.springframework.context.event.EventListener

interface RunnerEventServiceInterface {

  @EventListener(TriggerRunnerEvent::class)
  fun startNewRun(triggerEvent: TriggerRunnerEvent) : CsmEvent

  @EventListener(RunnerStatusEvent::class)
  fun getRunnerLastRunStatus(runnerStatusEvent: RunnerStatusEvent) : CsmRequestResponseEvent<String>
}