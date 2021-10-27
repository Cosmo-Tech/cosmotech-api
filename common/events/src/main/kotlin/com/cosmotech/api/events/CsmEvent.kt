// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

import org.springframework.context.ApplicationEvent

sealed class CsmEvent(publisher: Any) : ApplicationEvent(publisher)
