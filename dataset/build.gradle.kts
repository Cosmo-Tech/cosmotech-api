// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechConnectorApi)
  implementation("org.apache.commons:commons-compress:1.27.1")
  testImplementation("org.testng:testng:7.8.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
  testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.4")

  testImplementation(projects.cosmotechSolutionApi)
  testImplementation(projects.cosmotechWorkspaceApi)
}
