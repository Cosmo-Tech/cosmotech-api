// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechOrganizationApi)
  implementation("org.apache.commons:commons-compress:1.22")
  implementation("com.redislabs:jredisgraph:2.5.1") {
    constraints {
      implementation("org.apache.commons:commons-text:1.10.0")
    }
  }
  implementation("org.json:json:20220924")

  testImplementation("org.testng:testng:7.1.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.2")
  testImplementation("org.springframework.boot:spring-boot-starter-test:2.7.3")
}
