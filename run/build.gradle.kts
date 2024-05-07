// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation("io.argoproj.workflow:argo-client-java:v3.4.3")
  implementation("com.squareup.retrofit2:retrofit:2.9.0")
  implementation("com.squareup.retrofit2:converter-scalars:2.9.0")

  implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
  implementation("com.squareup.okhttp3:okhttp")
  implementation("com.squareup.okhttp3:logging-interceptor")
  implementation("org.springframework.boot:spring-boot-starter-amqp")

  implementation(projects.cosmotechConnectorApi)
  implementation(projects.cosmotechDatasetApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechWorkspaceApi)
  implementation(projects.cosmotechRunnerApi)

  testImplementation("org.testng:testng:7.8.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
  testImplementation("org.testcontainers:postgresql:1.19.7")
  testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.2")
}

tasks.withType<GenerateTask> { additionalProperties.put("modelMutable", false) }
