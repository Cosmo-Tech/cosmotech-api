// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechOrganizationApi)
  implementation("org.apache.commons:commons-compress:1.24.0")

  implementation("org.json:json:20231013")

  testImplementation("org.testng:testng:7.8.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
  testImplementation("org.springframework.boot:spring-boot-starter-test:3.1.5")
}
