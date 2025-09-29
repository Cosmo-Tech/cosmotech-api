// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.events

class UserRegistered(publisher: Any, val userId: String) : CsmEvent(publisher)

class UserUnregistered(publisher: Any, val userId: String) : CsmEvent(publisher)
