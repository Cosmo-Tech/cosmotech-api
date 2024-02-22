// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechScenariorunApi)
  implementation(projects.cosmotechScenarioApi)
  implementation(projects.cosmotechWorkspaceApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechDatasetApi)

  testImplementation(projects.cosmotechConnectorApi)
  testImplementation(projects.cosmotechDatasetApi)
  testImplementation("org.testng:testng:7.8.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
  testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.2")
}
