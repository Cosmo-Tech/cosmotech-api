import com.diffplug.gradle.spotless.SpotlessExtension
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.tools.jib.api.buildplan.ImageFormat.OCI
import com.google.cloud.tools.jib.gradle.JibExtension
import io.swagger.parser.OpenAPIParser
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

buildscript { dependencies { classpath("io.swagger.parser.v3:swagger-parser-v3:2.0.24") } }

plugins {
  val kotlinVersion = "1.4.32"
  kotlin("jvm") version kotlinVersion
  kotlin("plugin.spring") version kotlinVersion apply false

  id("com.diffplug.spotless") version "5.11.1"

  id("org.springframework.boot") version "2.4.4" apply false
  id("io.spring.dependency-management") version "1.0.11.RELEASE" apply false

  id("org.openapi.generator") version "5.1.0" apply false

  id("com.google.cloud.tools.jib") version "2.8.0" apply false
}

allprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "org.jetbrains.kotlin.jvm")

  repositories { mavenCentral() }

  group = "com.cosmotech"
  version = "0.0.1-SNAPSHOT"

  configure<SpotlessExtension> {
    isEnforceCheck = false

    val licenseHeaderComment = """
        // Copyright (c) Cosmo Tech.
        // Licensed under the MIT license.
      """.trimIndent()

    java {
      googleJavaFormat()
      target("**/*.java")
      licenseHeader(licenseHeaderComment)
    }
    kotlin {
      ktfmt()
      target("**/*.kt")
      licenseHeader(licenseHeaderComment)
    }
    kotlinGradle {
      ktfmt()
      target("**/*.kts")
      licenseHeader(licenseHeaderComment, "")
    }
  }
}

subprojects {
  apply(plugin = "org.jetbrains.kotlin.plugin.spring")
  apply(plugin = "org.springframework.boot")
  apply(plugin = "io.spring.dependency-management")

  // Apply some plugins to all projects except 'common'
  if (name != "cosmotech-api-common") {
    apply(plugin = "org.openapi.generator")
    apply(plugin = "com.google.cloud.tools.jib")
  }

  java { toolchain { languageVersion.set(JavaLanguageVersion.of(16)) } }

  dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("javax.validation:validation-api:2.0.1.Final")

    val springfoxVersion = "3.0.0"
    implementation("io.springfox:springfox-boot-starter:${springfoxVersion}")
    implementation("io.springfox:springfox-swagger-ui:${springfoxVersion}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    val developmentOnly = configurations.getByName("developmentOnly")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
  }

  if (name != "cosmotech-api-common") {
    tasks.withType<AbstractCompile> { dependsOn("openApiGenerate") }
    tasks.withType<GenerateTask> { dependsOn("openApiValidate") }
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions {
      freeCompilerArgs = listOf("-Xjsr305=strict")
      jvmTarget = "15"
    }
  }

  tasks.withType<Test> { useJUnitPlatform() }

  tasks.getByName<Jar>("jar") { enabled = true }

  if (name != "cosmotech-api-common") {

    val openApiFileDefinition =
        if (name == "cosmotech-api") {
          file("${rootDir}/openapi/openapi.yaml")
        } else {
          file("${projectDir}/src/main/openapi/${projectDir.relativeTo(rootDir)}s.yaml")
        }
    val jsonOutputFile = file("${buildDir}/tmp/static/openapi.json")
    tasks.register("convertOpenAPIYaml2Json") {
      inputs.file(openApiFileDefinition)
      outputs.file(jsonOutputFile)
      doLast {
        if (openApiFileDefinition.exists()) {
          val parseResult =
              OpenAPIParser().readContents(openApiFileDefinition.readText(), null, null)
          val openAPI = parseResult.openAPI
          if (!parseResult.messages.isNullOrEmpty() || openAPI == null) {
            throw IllegalStateException(
                "Unable to parse OpenAPI definition from $openApiFileDefinition : ${parseResult.messages}")
          }

          ObjectMapper()
              .apply { setSerializationInclusion(Include.NON_NULL) }
              .writerWithDefaultPrettyPrinter()
              .writeValue(jsonOutputFile, openAPI)
        } else {
          logger.warn(
              "Unable to find OpenAPI definition for project '${project.name}' => 'convertOpenAPIYaml2Json' not registered ! ")
        }
      }
    }

    tasks.getByName<Copy>("processResources") {
      dependsOn("convertOpenAPIYaml2Json")
      from("${buildDir}/tmp")
    }

    tasks.getByName<Copy>("processTestResources") {
      dependsOn("convertOpenAPIYaml2Json")
      from("${buildDir}/tmp")
    }

    tasks.getByName<BootJar>("bootJar") { classifier = "uberjar" }

    tasks.getByName<BootRun>("bootRun") {
      args = listOf("--spring.profiles.active=dev")
      properties.filterKeys { it.startsWith("csm.") }.forEach {
        systemProperty(it.key, it.value.toString())
      }
    }

    configure<SpringBootExtension> { buildInfo() }

    configure<JibExtension> {
      from { image = "openjdk:16-alpine" }
      to { image = "${project.group}/${project.name}:${project.version}" }
      container {
        format = OCI
        labels = mapOf("maintainer" to "Cosmo Tech")
        environment =
            mapOf(
                "JAVA_TOOL_OPTIONS" to
                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5005")
        ports = listOf("5005", "8080", "8081")
      }
    }
  }
}
