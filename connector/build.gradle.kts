// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins { id("org.jetbrains.kotlinx.kover") }

tasks.withType<GenerateTask> { additionalProperties.put("modelMutable", false) }

dependencies {}
