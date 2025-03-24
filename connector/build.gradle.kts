// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins { id("org.jetbrains.kotlinx.kover") }

tasks.withType<GenerateTask> { additionalProperties.put("modelMutable", false) }

dependencies {
  testImplementation("org.testng:testng:7.8.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
  testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.4")
}
