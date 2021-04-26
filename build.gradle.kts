import com.diffplug.gradle.spotless.SpotlessExtension
import com.google.cloud.tools.jib.api.buildplan.ImageFormat.OCI
import com.google.cloud.tools.jib.gradle.JibExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
  val kotlinVersion = "1.4.32"
  kotlin("jvm") version kotlinVersion
  kotlin("plugin.spring") version kotlinVersion apply false

  id("com.diffplug.spotless") version "5.12.4"

  id("org.springframework.boot") version "2.4.5" apply false
  id("io.spring.dependency-management") version "1.0.11.RELEASE" apply false

  id("org.openapi.generator") version "5.1.0" apply false

  id("com.google.cloud.tools.jib") version "3.0.0" apply false
}

allprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "org.jetbrains.kotlin.jvm")

  repositories { mavenCentral() }

  group = "com.cosmotech"
  version = "0.0.1-SNAPSHOT"

  configure<SpotlessExtension> {
    isEnforceCheck = false

    val licenseHeaderComment =
        """
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
      //      licenseHeader(licenseHeaderComment, "import")
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
    implementation("org.springframework.boot:spring-boot-starter-web") {
      exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("javax.validation:validation-api:2.0.1.Final")

    val swaggerParserVersion = "2.0.25"
    if (name == "cosmotech-api-common") {
      implementation("io.swagger.parser.v3:swagger-parser-v3:${swaggerParserVersion}")
    } else {
      testImplementation("io.swagger.parser.v3:swagger-parser-v3:${swaggerParserVersion}")
    }

    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config:2.0.2")

    val springDocVersion = "1.5.8"
    implementation("org.springdoc:springdoc-openapi-ui:${springDocVersion}")
    implementation("org.springdoc:springdoc-openapi-kotlin:${springDocVersion}")

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
    tasks.register<Copy>("copyOpenApiYamlToMainResources") {
      from(openApiFileDefinition)
      into("$buildDir/resources/main/static")
      rename { if (it != "openapi.yaml") "openapi.yaml" else it }
    }
    tasks.register<Copy>("copyOpenApiYamlToTestResources") {
      from(openApiFileDefinition)
      into("$buildDir/resources/test/static")
      rename { if (it != "openapi.yaml") "openapi.yaml" else it }
    }

    tasks.getByName<Copy>("processResources") { dependsOn("copyOpenApiYamlToMainResources") }

    tasks.getByName<Copy>("processTestResources") { dependsOn("copyOpenApiYamlToTestResources") }

    tasks.getByName<BootJar>("bootJar") { classifier = "uberjar" }

    tasks.getByName<BootRun>("bootRun") {
      args = listOf("--spring.profiles.active=dev")
      if (project.hasProperty("jvmArgs")) {
        jvmArgs = project.property("jvmArgs")?.toString()?.split("\\s+".toRegex()) ?: listOf()
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
