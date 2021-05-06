import com.diffplug.gradle.spotless.SpotlessExtension
import com.google.cloud.tools.jib.api.buildplan.ImageFormat.OCI
import com.google.cloud.tools.jib.gradle.JibExtension
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
  val kotlinVersion = "1.5.0"
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

  repositories {
    mavenLocal()
    maven {
      name = "Argo Client Java GitHub Packages"
      url = uri("https://maven.pkg.github.com/argoproj-labs/argo-client-java")
      credentials {
        username = project.findProperty("gpr.user")?.toString() ?: System.getenv("GITHUB_ACTOR")
        password = project.findProperty("gpr.key")?.toString() ?: System.getenv("GITHUB_TOKEN")
      }
      content { includeModule("io.argoproj.workflow", "argo-client-java") }
    }
    mavenCentral()
  }

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

  sourceSets {
    create("integrationTest") {
      compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
      runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
  }
  val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
  }
  val integrationTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntime.get())
  }

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

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config:2.0.2")

    val springDocVersion = "1.5.8"
    implementation("org.springdoc:springdoc-openapi-ui:${springDocVersion}")
    implementation("org.springdoc:springdoc-openapi-kotlin:${springDocVersion}")

    implementation("org.zalando:problem-spring-web-starter:0.27.0-RC.0")

    implementation("com.azure.spring:azure-spring-boot-starter-cosmos:3.4.0")

    testImplementation(platform("org.junit:junit-bom:5.7.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    integrationTestImplementation("org.springframework.boot:spring-boot-starter-test")

    val developmentOnly = configurations.getByName("developmentOnly")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
  }

  if (name != "cosmotech-api-common") {
    tasks.withType<AbstractCompile> { dependsOn("openApiGenerate") }
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions {
      languageVersion = "1.5"
      freeCompilerArgs = listOf("-Xjsr305=strict")
      jvmTarget = "11"
    }
  }

  val integrationTest =
      task<Test>("integrationTest") {
        description = "Runs integration tests"
        group = "verification"

        shouldRunAfter("test")
        classpath = sourceSets["integrationTest"].runtimeClasspath
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
      }

  tasks.check { dependsOn(integrationTest) }

  tasks.withType<Test> {
    val testWorkingDir = file("${buildDir}/run")
    workingDir = testWorkingDir

    doFirst { testWorkingDir.mkdirs() }

    useJUnitPlatform()

    if (project.hasProperty("test.exclude")) {
      exclude(project.property("test.exclude").toString())
    }

    ignoreFailures = System.getProperty("test.ignoreFailures")?.toBoolean() == true
    failFast = System.getProperty("test.failFast")?.toBoolean() == true

    System.getProperties()
        .filterKeys { it.toString().startsWith("test.") }
        .mapKeys { it.toString().substringAfter("test.") }
        .forEach { (key, value) -> systemProperty(key, value) }

    testLogging {
      events(TestLogEvent.FAILED)

      exceptionFormat = TestExceptionFormat.FULL
      showExceptions = true
      showCauses = true
      showStackTraces = true

      debug {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_ERROR,
            TestLogEvent.STANDARD_OUT)
        exceptionFormat = TestExceptionFormat.FULL
      }

      // remove standard output/error logging from --info builds by assigning only 'failed' and
      // 'skipped' events
      info {
        events(TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
      }
    }

    reports {
      junitXml.isEnabled = true
      html.isEnabled = true
    }
  }

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

    tasks.getByName<Copy>("processResources") {
      dependsOn("copyOpenApiYamlToMainResources")
      filesMatching("**/banner.txt") {
        filter<ReplaceTokens>("tokens" to mapOf("projectVersion" to project.version))
      }
    }

    tasks.getByName<Copy>("processTestResources") { dependsOn("copyOpenApiYamlToTestResources") }

    tasks.getByName<BootJar>("bootJar") { classifier = "uberjar" }

    tasks.getByName<BootRun>("bootRun") {
      args = listOf("--spring.profiles.active=dev")
      workingDir = rootDir
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
        jvmFlags =
            listOf(
                // Make sure Spring DevTools is disabled in production as running it is a
                // security risk
                "-Dspring.devtools.restart.enabled=false")
        ports = listOf("5005", "8080", "8081")
      }
    }
  }
}
