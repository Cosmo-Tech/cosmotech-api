// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run

import com.cosmotech.api.events.AskRunStatusEvent
import org.springframework.context.event.EventListener

interface RunEventServiceInterface {
  @EventListener(AskRunStatusEvent::class) fun getRunStatus(askRunStatusEvent: AskRunStatusEvent)
}
