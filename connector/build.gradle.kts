// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

dependencies {
  api(projects.cosmotechApiCommonParent.cosmotechApiCommon)
  api(projects.cosmotechApiCommonParent.cosmotechApiCommonAzure)
}

tasks.withType<GenerateTask> { additionalProperties.put("modelMutable", false) }
