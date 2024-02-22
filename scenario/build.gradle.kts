// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechDatasetApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechWorkspaceApi)

  testImplementation(projects.cosmotechConnectorApi)
  testImplementation(projects.cosmotechRunApi)
  testImplementation("org.testng:testng:7.8.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
  testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.2")
}
