// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(CsmPlatformProperties::class)
class CsmApiConfiguration
