// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

dependencies {
  implementation("io.argoproj.workflow:argo-client-java:v3.0.1")
  implementation("com.squareup.retrofit2:retrofit:2.9.0")
  implementation("com.squareup.retrofit2:converter-scalars:2.9.0")

  implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
  implementation("com.squareup.okhttp3:okhttp")
  implementation("com.squareup.okhttp3:logging-interceptor")

  api(projects.cosmotechApiCommonParent.cosmotechApiCommon)
  api(projects.cosmotechApiCommonParent.cosmotechApiCommonAzure)
  implementation(projects.cosmotechConnectorApi)
  implementation(projects.cosmotechDatasetApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechScenarioApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechWorkspaceApi)
}

tasks.withType<GenerateTask> { additionalProperties.put("modelMutable", false) }
