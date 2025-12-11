// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins { id("org.jetbrains.kotlinx.kover") }

val argoClientJavaVersion = "v3.6.13"
val retroFitVersion = "3.0.0"
val okHttpBom = "5.3.2"

dependencies {
  implementation(projects.cosmotechDatasetApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechWorkspaceApi)
  implementation(projects.cosmotechRunnerApi)
  implementation(projects.cosmotechCommonApi)

  implementation("io.argoproj.workflow:argo-client-java:$argoClientJavaVersion")
  implementation("com.squareup.retrofit2:retrofit:$retroFitVersion")
  implementation("com.squareup.retrofit2:converter-scalars:$retroFitVersion")
  implementation(platform("com.squareup.okhttp3:okhttp-bom:$okHttpBom"))
  implementation("com.squareup.okhttp3:okhttp")
  implementation("com.squareup.okhttp3:logging-interceptor")
}

tasks.withType<GenerateTask> { additionalProperties.put("modelMutable", false) }
