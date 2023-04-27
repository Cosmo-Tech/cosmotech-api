// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)

  testImplementation("org.testng:testng:7.1.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.2")
  testImplementation("org.springframework.boot:spring-boot-starter-test:2.7.3")
}
