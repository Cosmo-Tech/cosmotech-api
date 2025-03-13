// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.*
import com.github.jk1.license.task.ReportTask
import com.google.cloud.tools.jib.api.buildplan.ImageFormat.OCI
import com.google.cloud.tools.jib.gradle.JibExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

// TODO This build script does way too much things.
// Consider refactoring it by extracting these custom tasks and plugin
// implementations/configurations in a 'buildSrc' included build.
// See https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#sec:build_sources

buildscript {
  configurations.all {
    // Due to bug OpenAPITools/openapi-generator#20375 when we use another
    // plugin that also depends on jmustache the newer version ends up being
    // used and it breaks the generation of the python client.
    resolutionStrategy.force("com.samskivert:jmustache:1.15")
  }
}

plugins {
  val kotlinVersion = "1.9.23"
  kotlin("jvm") version kotlinVersion
  kotlin("plugin.spring") version kotlinVersion apply false
  id("pl.allegro.tech.build.axion-release") version "1.18.17"
  id("com.diffplug.spotless") version "7.0.2"
  id("org.springframework.boot") version "3.4.1" apply false
  id("project-report")
  id("org.owasp.dependencycheck") version "12.0.0"
  id("com.github.jk1.dependency-license-report") version "2.9"
  id("org.jetbrains.kotlinx.kover") version "0.7.4"
  id("io.gitlab.arturbosch.detekt") version "1.23.8"
  id("org.openapi.generator") version "7.10.0" apply false
  id("com.google.cloud.tools.jib") version "3.4.4" apply false
}

scmVersion { tag { prefix.set("") } }

group = "com.cosmotech"

version = scmVersion.version

// Dependencies version

// Required versions
val jacksonVersion = "2.15.3"
val springWebVersion = "6.2.1"

// Implementation
val kotlinJvmTarget = 21
val cosmotechApiCommonVersion = "2.1.0-SNAPSHOT"
val jedisVersion = "4.4.6"
val springOauthVersion = "6.4.2"
val redisOmSpringVersion = "0.9.7"
val kotlinCoroutinesCoreVersion = "1.8.1"
val oktaSpringBootVersion = "3.0.7"
val springDocVersion = "2.5.0"
val swaggerParserVersion = "2.1.24"
val commonsCsvVersion = "1.10.0"
val apiValidationVersion = "3.0.2"
val kubernetesClientVersion = "22.0.0"

// Checks
val detektVersion = "1.23.7"

// Tests
val jUnitBomVersion = "5.10.0"
val mockkVersion = "1.13.13"
val awaitilityKVersion = "4.2.0"
val testcontainersRedis = "1.6.4"
val springMockkVersion = "4.0.2"

var licenseReportDir = "$projectDir/doc/licenses"

val configBuildDir = "${layout.buildDirectory.get()}/config"

mkdir(configBuildDir)

val hardCodedLicensesReportPath = "project-licenses-for-check-license-task.json"

dependencyCheck {
  // Configure dependency check plugin. It checks for publicly disclosed
  // vulnerabilities in project dependencies. To use it, you need to have an
  // API key from the NVD (National Vulnerability Database), pass it by setting
  // the environment variable NVD_API_key (See project README.md: Static Code
  // Analysis -> Vulnerability report).
  nvd { apiKey = System.getenv("NVD_API_key") }
}

licenseReport {
  outputDir = licenseReportDir
  allowedLicensesFile =
      "https://raw.githubusercontent.com/Cosmo-Tech/cosmotech-license/refs/heads/main/config/allowed-licenses.json"
  val bundle =
      "https://raw.githubusercontent.com/Cosmo-Tech/cosmotech-license/refs/heads/main/config/license-normalizer-bundle.json"

  renderers =
      arrayOf<ReportRenderer>(
          InventoryHtmlReportRenderer("index.html"),
          JsonReportRenderer("project-licenses-for-check-license-task.json", false))
  filters =
      arrayOf<LicenseBundleNormalizer>(
          LicenseBundleNormalizer(uri(bundle).toURL().openStream(), true))
}

allprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "io.gitlab.arturbosch.detekt")
  apply(plugin = "project-report")
  apply(plugin = "org.owasp.dependencycheck")

  version = rootProject.scmVersion.version ?: error("Root project did not configure scmVersion!")

  java {
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain { languageVersion.set(JavaLanguageVersion.of(kotlinJvmTarget)) }
  }
  configurations { all { resolutionStrategy { force("com.redis.om:redis-om-spring:0.9.1") } } }

  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/Cosmo-Tech/cosmotech-api-common")
      credentials {
        username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
        password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
      }
    }
    maven {
      name = "Argo Client Java GitHub Packages"
      url = uri("https://maven.pkg.github.com/argoproj/argo-client-java")
      credentials {
        username = project.findProperty("gpr.user")?.toString() ?: System.getenv("GITHUB_ACTOR")
        password = project.findProperty("gpr.key")?.toString() ?: System.getenv("GITHUB_TOKEN")
      }
      content { includeModule("io.argoproj.workflow", "argo-client-java") }
    }
    mavenCentral()
  }

  tasks.withType<HtmlDependencyReportTask>().configureEach { projects = project.allprojects }

  configure<SpotlessExtension> {
    isEnforceCheck = false

    val licenseHeaderComment =
        """
        // Copyright (c) Cosmo Tech.
        // Licensed under the MIT license.
      """
            .trimIndent()

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

  tasks.withType<JavaCompile>() { options.compilerArgs.add("-parameters") }
}

subprojects {
  apply(plugin = "org.jetbrains.kotlin.plugin.spring")
  apply(plugin = "org.springframework.boot")
  apply(plugin = "org.openapi.generator")
  apply(plugin = "com.google.cloud.tools.jib")

  val projectDirName = projectDir.relativeTo(rootDir).name
  val openApiDefinitionFile = file("${projectDir}/src/main/openapi/${projectDirName}.yaml")

  val openApiServerSourcesGenerationDir =
      "${layout.buildDirectory.get()}/generated-sources/openapi/kotlin-spring"

  val testWorkingDirPath = "${layout.buildDirectory.get()}/run"

  sourceSets {
    create("integrationTest") {
      compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
      runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
    if (openApiDefinitionFile.exists()) {
      main { java.srcDirs("$openApiServerSourcesGenerationDir/src/main/kotlin") }
      test { java.srcDirs("$openApiServerSourcesGenerationDir/src/test/kotlin") }
    }
  }

  val integrationTestImplementation by
      configurations.getting { extendsFrom(configurations.testImplementation.get()) }

  @Suppress("UNUSED_VARIABLE")
  val integrationTestRuntimeOnly by
      configurations.getting { extendsFrom(configurations.testRuntimeOnly.get()) }

  tasks.withType<Detekt>().configureEach {
    buildUponDefaultConfig = true // preconfigure defaults
    allRules = false // activate all available (even unstable) rules.
    autoCorrect = true
    config.from(file("$rootDir/.detekt/detekt.yaml"))
    jvmTarget = kotlinJvmTarget.toString()
    ignoreFailures = project.findProperty("detekt.ignoreFailures")?.toString()?.toBoolean() ?: false
    // Specify the base path for file paths in the formatted reports.
    // If not set, all file paths reported will be absolute file path.
    // This is so we can easily map results onto their source files in tools like GitHub Code
    // Scanning
    basePath = rootDir.absolutePath
    reports {
      html {
        // observe findings in your browser with structure and code snippets
        required.set(true)
        outputLocation.set(
            file("${layout.buildDirectory.get()}/reports/detekt/${project.name}-detekt.html"))
      }
      xml {
        // checkstyle like format mainly for integrations like Jenkins
        required.set(false)
        outputLocation.set(
            file("${layout.buildDirectory.get()}/reports/detekt/${project.name}-detekt.xml"))
      }
      txt {
        // similar to the console output, contains issue signature to manually edit baseline files
        required.set(true)
        outputLocation.set(
            file("${layout.buildDirectory.get()}/reports/detekt/${project.name}-detekt.txt"))
      }
      sarif {
        // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations
        // with Github Code Scanning
        required.set(true)
        outputLocation.set(
            file("${layout.buildDirectory.get()}/reports/detekt/${project.name}-detekt.sarif"))
      }
    }

    tasks.getByName<BootJar>("bootJar") { enabled = false }
    tasks.getByName<Jar>("jar") { enabled = true }
  }

  dependencies {
    detekt("io.gitlab.arturbosch.detekt:detekt-cli:$detektVersion")
    detekt("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesCoreVersion")

    implementation(
        platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-web") {
      exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-undertow") {
      constraints {
        implementation("org.jboss.xnio:xnio-api:3.8.16.Final")
        implementation("io.undertow:undertow-core:2.3.16.Final")
      }
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    // https://mvnrepository.com/artifact/jakarta.validation/jakarta.validation-api
    implementation("jakarta.validation:jakarta.validation-api:$apiValidationVersion")
    implementation("io.kubernetes:client-java:${kubernetesClientVersion}")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${springDocVersion}")
    implementation("io.swagger.parser.v3:swagger-parser-v3:${swaggerParserVersion}")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("com.okta.spring:okta-spring-boot-starter:${oktaSpringBootVersion}")

    implementation("org.apache.commons:commons-csv:$commonsCsvVersion")
    implementation("com.redis.om:redis-om-spring:${redisOmSpringVersion}") {
      constraints { implementation("ai.djl:api:0.28.0") }
    }
    implementation("org.springframework.data:spring-data-redis")
    implementation("org.springframework:spring-jdbc")
    implementation("org.postgresql:postgresql")

    implementation("org.json:json:20240303")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:$jUnitBomVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.awaitility:awaitility-kotlin:$awaitilityKVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    integrationTestImplementation("org.springframework.boot:spring-boot-starter-test") {
      // Drop legacy Junit < 5
      exclude(module = "junit")
      exclude(module = "mockito-core")
    }
    integrationTestImplementation("com.ninja-squad:springmockk:$springMockkVersion")
    // developmentOnly("org.springframework.boot:spring-boot-devtools")
    integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    api("com.github.Cosmo-Tech:cosmotech-api-common:$cosmotechApiCommonVersion")
  }

  tasks.withType<KotlinCompile> {
    if (openApiDefinitionFile.exists()) {
      dependsOn("openApiGenerate")
    }
  }

  tasks.withType<JavaCompile> {
    val compilerArgs = options.compilerArgs
    compilerArgs.add("-parameters")
  }

  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      languageVersion = "1.9"
      freeCompilerArgs = listOf("-Xjsr305=strict")
      jvmTarget = kotlinJvmTarget.toString()
      java {
        targetCompatibility = JavaVersion.VERSION_21
        sourceCompatibility = JavaVersion.VERSION_21
        toolchain { languageVersion.set(JavaLanguageVersion.of(kotlinJvmTarget)) }
      }
    }
  }

  val integrationTest =
      task<Test>("integrationTest") {
        description = "Runs integration tests"
        group = "verification"
        useJUnitPlatform()
        shouldRunAfter("test")
        classpath = sourceSets["integrationTest"].runtimeClasspath
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        finalizedBy("copyAdocFiles")
      }

  tasks.register<Copy>("copyAdocFiles") {
    dependsOn("integrationTest")
    from("$testWorkingDirPath/build/generated-snippets")
    into("${rootDir}/doc/generated-snippets")
  }

  tasks.check { dependsOn(integrationTest) }

  tasks.withType<Test> {
    val testWorkingDir = file(testWorkingDirPath)
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
        .forEach { systemProperty(it.key, it.value) }

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

      // remove standard output/error logging from --info builds
      info {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
      }
    }

    reports {
      junitXml.required.set(true)
      html.required.set(true)
    }
  }

  tasks.getByName<Jar>("jar") { enabled = true }

  if (openApiDefinitionFile.exists()) {
    logger.info("Found OpenAPI definition: $openApiDefinitionFile")

    tasks.withType<ValidateTask> {
      inputSpec.set("${projectDir}/src/main/openapi/${projectDirName}.yaml")
    }

    tasks.withType<GenerateTask> {
      inputSpec.set("${projectDir}/src/main/openapi/${projectDirName}.yaml")
      outputDir.set(openApiServerSourcesGenerationDir)
      generatorName.set("kotlin-spring")
      apiPackage.set("com.cosmotech.${projectDirName}.api")
      modelPackage.set("com.cosmotech.${projectDirName}.domain")
      globalProperties.set(
          mapOf(
              "apiDocs" to "true",
              // Excluded because the OpenAPI Generator generates test classes that expect the
              // Service Implementation to be present in the 'apiPackage' package,
              // which is not the case when serviceInterface is true.
              // We will write our own tests instead.
              "apiTests" to "false"))
      additionalProperties.set(
          mapOf(
              "title" to "Cosmo Tech ${projectDirName.capitalizeAsciiOnly()} Manager API",
              "basePackage" to "com.cosmotech",
              "configPackage" to "com.cosmotech.${projectDirName}.config",
              "enumPropertyNaming" to "original",
              "exceptionHandler" to false,
              "serviceInterface" to true,
              "documentationProvider" to "none",
              "useSpringBoot3" to true,
              "useTags" to true,
              "beanQualifiers" to true,
              "modelMutable" to true))
    }
  }

  val openApiFileDefinition =
      if (name == "cosmotech-api") {
        file("${rootDir}/openapi/openapi.yaml")
      } else {
        openApiDefinitionFile
      }
  tasks.register<Copy>("copyOpenApiYamlToMainResources") {
    from(openApiFileDefinition)
    into("${layout.buildDirectory.get()}/resources/main/static")
    rename { if (it != "openapi.yaml") "openapi.yaml" else it }
  }
  tasks.register<Copy>("copyOpenApiYamlToTestResources") {
    from(openApiFileDefinition)
    into("${layout.buildDirectory.get()}/resources/test/static")
    rename { if (it != "openapi.yaml") "openapi.yaml" else it }
  }

  var fullVersion = project.version.toString()
  var buildVersion = ""
  if (!rootProject.scmVersion.scmPosition.revision.isNullOrEmpty()) {
    buildVersion = "${rootProject.scmVersion.scmPosition.revision.substring(0, 8)}"
    fullVersion = "$fullVersion-$buildVersion"
  }
  tasks.getByName<Copy>("processResources") {
    dependsOn("copyOpenApiYamlToMainResources")
    filesMatching("**/banner.txt") {
      filter<ReplaceTokens>("tokens" to mapOf("projectVersion" to fullVersion))
    }
    filesMatching("**/about.json") {
      filter<ReplaceTokens>(
          "tokens" to
              mapOf(
                  "fullVersion" to fullVersion,
                  "releaseVersion" to project.version,
                  "buildVersion" to buildVersion))
    }
  }

  tasks.register<Copy>("copyAboutJsonToTestResources") {
    from("${rootDir}/api/src/main/resources/about.json")
    into("${layout.buildDirectory.get()}/resources/test/")
    filter<ReplaceTokens>(
        "tokens" to
            mapOf(
                "fullVersion" to fullVersion,
                "releaseVersion" to project.version,
                "buildVersion" to buildVersion))
  }
  tasks.getByName<Copy>("processTestResources") {
    dependsOn("copyOpenApiYamlToTestResources")
    dependsOn("copyAboutJsonToTestResources")
  }

  tasks.getByName<BootJar>("bootJar") { archiveClassifier.set("uberjar") }

  tasks.getByName<BootRun>("bootRun") {
    workingDir = rootDir

    environment("CSM_PLATFORM_VENDOR", project.findProperty("platform")?.toString() ?: "")
    project.findProperty("identityProvider")?.toString()?.let {
      environment("IDENTITY_PROVIDER", it)
    }

    if (project.hasProperty("jvmArgs")) {
      jvmArgs = project.property("jvmArgs").toString().split("\\s+".toRegex()).toList()
    }

    args = listOf("--spring.profiles.active=dev")
  }

  configure<JibExtension> {
    from { image = "eclipse-temurin:21-alpine" }
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
      // Docker Best Practice : run as non-root.
      // These are the 'nobody' UID and GID inside the image
      user = "65534:65534"
    }
  }
}

val copySubProjectsDetektReportsTasks =
    subprojects.flatMap { subProject ->
      listOf("html", "xml", "txt", "sarif").map { format ->
        val formatCapitalized = format.capitalizeAsciiOnly()
        val copyTask =
            tasks.register<Copy>(
                "detektCopy${formatCapitalized}ReportFor" +
                    "${subProject.projectDir.relativeTo(rootDir)}"
                        .capitalizeAsciiOnly()
                        .replace("/", "_")) {
                  dependsOn("spotlessKotlin", "spotlessKotlinGradle", "spotlessJava")
                  from(
                      file(
                          "${subProject.projectDir}/build/reports/detekt/${subProject.name}-detekt.$format"))
                  into(
                      "${subProject.parent!!.layout.projectDirectory}/build/reports/detekt/$format")
                }
        subProject.tasks.getByName("detekt") { finalizedBy(copyTask) }
        copyTask
      }
    }

tasks.register<Copy>("copySubProjectsDetektReports") {
  shouldRunAfter("detekt")
  dependsOn(*copySubProjectsDetektReportsTasks.toTypedArray())
}

tasks.getByName("detekt") { finalizedBy("copySubProjectsDetektReports") }

extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverReportExtension> {
  defaults {
    // reports configs for XML, HTML, verify reports
  }
  filters {
    excludes {
      projects { excludes { cosmotechApi } }
      classes("com.cosmotech.Application*")
      classes("com.cosmotech.*.api.*")
      classes("com.cosmotech.*.domain.*")
    }
  }
}

// https://github.com/jk1/Gradle-License-Report/blob/master/README.md
tasks.register<ReportTask>("generateLicenseDoc") {}
