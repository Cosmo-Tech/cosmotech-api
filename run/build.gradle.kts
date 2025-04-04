// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins { id("org.jetbrains.kotlinx.kover") }

val argoClientJavaVersion = "v3.5.11"
val retroFitVersion = "2.9.0"
val okHttpBom = "4.10.0"
val testContainersRabbitMQVersion = "1.20.6"
val springRabbitMQTestVersion = "3.1.4"

dependencies {
  implementation(projects.cosmotechConnectorApi)
  implementation(projects.cosmotechDatasetApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechWorkspaceApi)
  implementation(projects.cosmotechRunnerApi)

  implementation("io.argoproj.workflow:argo-client-java:$argoClientJavaVersion")
  implementation("com.squareup.retrofit2:retrofit:$retroFitVersion")
  implementation("com.squareup.retrofit2:converter-scalars:$retroFitVersion")
  implementation(platform("com.squareup.okhttp3:okhttp-bom:$okHttpBom"))
  implementation("com.squareup.okhttp3:okhttp")
  implementation("com.squareup.okhttp3:logging-interceptor")
  implementation("org.springframework.boot:spring-boot-starter-amqp")

  testImplementation("org.testcontainers:rabbitmq:$testContainersRabbitMQVersion")
  testImplementation("org.springframework.amqp:spring-rabbit-test:$springRabbitMQTestVersion")
}

tasks.withType<GenerateTask> { additionalProperties.put("modelMutable", false) }
