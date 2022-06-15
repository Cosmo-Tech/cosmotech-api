// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

tasks.withType<GenerateTask> { additionalProperties.put("modelMutable", false) }
