import com.diffplug.gradle.spotless.SpotlessExtension
import com.google.cloud.tools.jib.api.buildplan.ImageFormat.OCI
import com.google.cloud.tools.jib.gradle.JibExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
  val kotlinVersion = "1.5.20"
  kotlin("jvm") version kotlinVersion
  kotlin("plugin.spring") version kotlinVersion apply false

  id("com.diffplug.spotless") version "5.14.0"

  id("org.springframework.boot") version "2.5.2" apply false
  id("io.spring.dependency-management") version "1.0.11.RELEASE"

  id("org.openapi.generator") version "5.1.1" apply false

  id("com.google.cloud.tools.jib") version "3.1.1" apply false

  id("io.gitlab.arturbosch.detekt") version "1.17.1"
}

allprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "io.gitlab.arturbosch.detekt")

  repositories {
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
  version = "0.0.8-SNAPSHOT"

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

  dependencyManagement { imports { mavenBom("com.azure.spring:azure-spring-boot-bom:3.6.0") } }

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
    extendsFrom(configurations.testRuntimeOnly.get())
  }

  val kotlinJvmTarget = "11"

  tasks.withType<Detekt> {
    buildUponDefaultConfig = true // preconfigure defaults
    allRules = false // activate all available (even unstable) rules.
    config.from(file("$rootDir/.detekt/detekt.yaml"))
    jvmTarget = kotlinJvmTarget
    ignoreFailures = project.findProperty("detekt.ignoreFailures")?.toString()?.toBoolean() ?: true
    reports {
      html {
        // observe findings in your browser with structure and code snippets
        enabled = true
        destination = file("$buildDir/reports/detekt/${project.name}-detekt.html")
      }
      xml {
        // checkstyle like format mainly for integrations like Jenkins
        enabled = false
        destination = file("$buildDir/reports/detekt/${project.name}-detekt.xml")
      }
      txt {
        // similar to the console output, contains issue signature to manually edit baseline files
        enabled = true
        destination = file("$buildDir/reports/detekt/${project.name}-detekt.txt")
      }
      sarif {
        // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations
        // with Github Code Scanning
        enabled = true
        destination = file("$buildDir/reports/detekt/${project.name}-detekt.sarif")
      }
    }
  }

  dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web") {
      exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("javax.validation:validation-api:2.0.1.Final")

    val springDocVersion = "1.5.9"
    implementation("org.springdoc:springdoc-openapi-ui:${springDocVersion}")
    implementation("org.springdoc:springdoc-openapi-kotlin:${springDocVersion}")

    implementation("org.zalando:problem-spring-web-starter:0.27.0-RC.0")

    // TODO Extract those dependencies in a 'common/azure' sub-project,
    //  included dynamically if the 'platform' build property is 'azure'
    implementation("com.azure:azure-storage-blob-batch:12.10.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(
        "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:2.5.2")
    implementation("org.springframework.security:spring-security-jwt:1.1.1.RELEASE")
    implementation("com.azure.spring:azure-spring-boot-starter-cosmos")
    implementation("com.azure.spring:azure-spring-boot-starter-storage")
    implementation("com.azure.spring:azure-spring-boot-starter-active-directory")
    // com.azure.spring:azure-spring-boot-starter-active-directory provides this dependency
    // transitively,
    // but its version is incompatible at runtime with what is expected by
    // spring-security-oauth2-jose
    implementation("com.nimbusds:nimbus-jose-jwt:9.10.1")
    implementation("org.springframework.security:spring-security-oauth2-jose:5.5.1")
    implementation("org.springframework.security:spring-security-oauth2-resource-server:5.5.1")
    //    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.12.0")

    integrationTestImplementation("org.springframework.boot:spring-boot-starter-test") {
      // Drop legacy Junit < 5
      exclude(module = "junit")
      exclude(module = "mockito-core")
    }
    integrationTestImplementation("com.ninja-squad:springmockk:3.0.1")

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
      jvmTarget = kotlinJvmTarget
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
        .mapKeys { entry -> entry.key.toString().substringAfter("test.") }
        .forEach(this::systemProperty)

    testLogging {
      events(TestLogEvent.FAILED)

      exceptionFormat = TestExceptionFormat.FULL
      showExceptions = true
      showCauses = true
      showStackTraces = true

      debug {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_ERROR,
            TestLogEvent.STANDARD_OUT)
        exceptionFormat = TestExceptionFormat.FULL
      }

      // remove standard output/error logging from --info builds by assigning only 'failed' and
      // 'skipped' events
      info {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
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
      workingDir = rootDir

      environment("CSM_PLATFORM_VENDOR", project.findProperty("platform")?.toString() ?: "azure")

      if (project.hasProperty("jvmArgs")) {
        jvmArgs = project.property("jvmArgs").toString().split("\\s+".toRegex()).toList()
      }

      args = listOf("--spring.profiles.active=dev")
    }

    configure<SpringBootExtension> {
      buildInfo {
        properties {
          // Unsetting time so the task can be deterministic and cacheable, for performance reasons
          time = null
        }
      }
    }

    configure<JibExtension> {
      from { image = "openjdk:16-alpine" }
      to { image = "${project.group}/${project.name}:${project.version}" }
      container {
        format = OCI
        labels.putAll(mapOf("maintainer" to "Cosmo Tech"))
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

val copySubProjectsDetektReportsTasks =
    listOf("html", "xml", "txt", "sarif").map { format ->
      tasks.register<Copy>("copySubProjects${format.capitalize()}DetektReports") {
        subprojects.forEach { dependsOn(it.tasks.getByName("detekt")) }
        val detektReports =
            subprojects.map {
              file("${it.projectDir}/build/reports/detekt/${it.name}-detekt.$format")
            }
        from(*detektReports.toTypedArray())
        into("${rootProject.buildDir}/reports/detekt/$format")
      }
    }

tasks.register<Copy>("copySubProjectsDetektReports") {
  dependsOn(*copySubProjectsDetektReportsTasks.toTypedArray())
}

tasks.getByName("detekt") { finalizedBy("copySubProjectsDetektReports") }
