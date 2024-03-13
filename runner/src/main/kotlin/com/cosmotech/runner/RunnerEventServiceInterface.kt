// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner

import com.cosmotech.api.events.TriggerRunnerEvent
import org.springframework.context.event.EventListener

interface RunnerEventServiceInterface {

  @EventListener(TriggerRunnerEvent::class) fun startNewRun(triggerEvent: TriggerRunnerEvent)
}
