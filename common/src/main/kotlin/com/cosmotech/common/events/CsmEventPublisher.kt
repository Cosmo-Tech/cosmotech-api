// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.events

interface CsmEventPublisher {

  fun <T : CsmEvent> publishEvent(event: T)
}
