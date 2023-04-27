// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  testImplementation("org.testng:testng:7.7.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.2")
  testImplementation("org.springframework.boot:spring-boot-starter-test:2.7.3")
}
