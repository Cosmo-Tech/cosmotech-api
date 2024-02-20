// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechDatasetApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechWorkspaceApi)
  implementation("com.redislabs:jredisgraph:2.5.1") {
    constraints { implementation("org.apache.commons:commons-text:1.10.0") }
  }

  testImplementation(projects.cosmotechConnectorApi)
  testImplementation("org.testng:testng:7.1.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.2")
  testImplementation("org.springframework.boot:spring-boot-starter-test:2.7.3")
}
