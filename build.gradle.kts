// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.*
import com.github.jk1.license.task.CheckLicenseTask
import com.github.jk1.license.task.ReportTask
import com.google.cloud.tools.jib.api.buildplan.ImageFormat.OCI
import com.google.cloud.tools.jib.gradle.JibExtension
import io.gitlab.arturbosch.detekt.Detekt
import java.io.FileOutputStream
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

// TODO This build script does way too much things.
// Consider refactoring it by extracting these custom tasks and plugin
// implementations/configurations in a 'buildSrc' included build.
// See https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#sec:build_sources

plugins {
  val kotlinVersion = "1.8.0"
  kotlin("jvm") version kotlinVersion
  kotlin("plugin.spring") version kotlinVersion apply false
  id("pl.allegro.tech.build.axion-release") version "1.14.2"
  id("com.diffplug.spotless") version "6.11.0"
  id("org.springframework.boot") version "2.7.11" apply false
  id("org.openapi.generator") version "5.4.0" apply false
  id("com.google.cloud.tools.jib") version "3.3.1" apply false
  id("io.gitlab.arturbosch.detekt") version "1.21.0"
  id("org.jetbrains.kotlinx.kover") version "0.6.1"
  id("project-report")
  id("org.owasp.dependencycheck") version "8.2.1"
  id("com.github.jk1.dependency-license-report") version "1.17"
}

scmVersion { tag { prefix.set("") } }

group = "com.cosmotech"

version = scmVersion.version

val kotlinJvmTarget = 17
val cosmotechApiCommonVersion = "0.3.3"
val cosmotechApiAzureVersion = "0.2.1"
val azureSpringBootBomVersion = "3.14.0"
val jedisVersion = "3.9.0"
val springOauthVersion = "5.8.3"
val jredistimeseriesVersion = "1.6.0"
val redisOmSpringVersion = "0.6.4"

var licenseReportDir = "$projectDir/doc/licenses"

val configBuildDir = "$buildDir/config"

mkdir(configBuildDir)

fun downloadLicenseConfigFile(name: String): String {
  val localPath = "$configBuildDir/$name"
  val f = file(localPath)
  f.delete()
  val url = "https://raw.githubusercontent.com/Cosmo-Tech/cosmotech-license/main/config/$name"
  logger.info("Downloading license config file from $url to $localPath")
  uri(url).toURL().openStream().use { it.copyTo(FileOutputStream(f)) }
  return localPath
}

val licenseNormalizerPath = downloadLicenseConfigFile("license-normalizer-bundle.json")
val licenseAllowedPath =
    if (project.properties["useLocalLicenseAllowedFile"] == "true") {
      "$projectDir/config/allowed-licenses.json"
    } else {
      downloadLicenseConfigFile("allowed-licenses.json")
    }

logger.info("Using licenses allowed file: $licenseAllowedPath")

val licenseEmptyPath = downloadLicenseConfigFile("empty-dependencies-resume.json")
// Plugin uses a generated report to check the licenses in a prepation task
val hardCodedLicensesReportPath = "project-licenses-for-check-license-task.json"

licenseReport {
  outputDir = licenseReportDir
  allowedLicensesFile = file(licenseAllowedPath)
  renderers =
      arrayOf<ReportRenderer>(
          InventoryHtmlReportRenderer("index.html"),
          JsonReportRenderer("project-licenses-for-check-license-task.json", false))
  filters = arrayOf<LicenseBundleNormalizer>(LicenseBundleNormalizer(licenseNormalizerPath, true))
}

allprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "io.gitlab.arturbosch.detekt")
  apply(plugin = "project-report")
  apply(plugin = "org.owasp.dependencycheck")

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
      ktfmt("0.41")
      target("**/*.kt")
      licenseHeader(licenseHeaderComment)
    }
    kotlinGradle {
      ktfmt("0.41")
      target("**/*.kts")
      //      licenseHeader(licenseHeaderComment, "import")
    }
  }
}

subprojects {
  apply(plugin = "org.jetbrains.kotlin.plugin.spring")
  apply(plugin = "org.springframework.boot")
  apply(plugin = "org.openapi.generator")
  apply(plugin = "com.google.cloud.tools.jib")

  version = rootProject.scmVersion.version ?: error("Root project did not configure scmVersion!")

  java { toolchain { languageVersion.set(JavaLanguageVersion.of(kotlinJvmTarget)) } }

  val projectDirName = projectDir.relativeTo(rootDir).name
  val openApiDefinitionFile = file("${projectDir}/src/main/openapi/${projectDirName}.yaml")

  val openApiServerSourcesGenerationDir = "${buildDir}/generated-sources/openapi/kotlin-spring"

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
        outputLocation.set(file("$buildDir/reports/detekt/${project.name}-detekt.html"))
      }
      xml {
        // checkstyle like format mainly for integrations like Jenkins
        required.set(false)
        outputLocation.set(file("$buildDir/reports/detekt/${project.name}-detekt.xml"))
      }
      txt {
        // similar to the console output, contains issue signature to manually edit baseline files
        required.set(true)
        outputLocation.set(file("$buildDir/reports/detekt/${project.name}-detekt.txt"))
      }
      sarif {
        // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations
        // with Github Code Scanning
        required.set(true)
        outputLocation.set(file("$buildDir/reports/detekt/${project.name}-detekt.sarif"))
      }
    }

    tasks.getByName<BootJar>("bootJar") { enabled = false }
    tasks.getByName<Jar>("jar") { enabled = true }
  }

  dependencies {

    // Workaround until Detekt adds support for JVM Target 17
    // See https://github.com/detekt/detekt/issues/4287
    detekt("io.gitlab.arturbosch.detekt:detekt-cli:1.19.0")
    detekt("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.7.20")

    val developmentOnly = configurations.getByName("developmentOnly")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3-native-mt")

    implementation(
        platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    developmentOnly(
        platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-web") {
      exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("javax.validation:validation-api:2.0.1.Final")

    val springDocVersion = "1.6.14"
    implementation("org.springdoc:springdoc-openapi-ui:${springDocVersion}")
    implementation("org.springdoc:springdoc-openapi-kotlin:${springDocVersion}")
    val swaggerParserVersion = "2.1.13"
    implementation("io.swagger.parser.v3:swagger-parser-v3:${swaggerParserVersion}")

    implementation("org.zalando:problem-spring-web-starter:0.27.0")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-jose:${springOauthVersion}")
    implementation(
        "org.springframework.security:spring-security-oauth2-resource-server:${springOauthVersion}")
    val oktaSpringBootVersion = "2.1.6"
    implementation("com.okta.spring:okta-spring-boot-starter:${oktaSpringBootVersion}")

    implementation("redis.clients:jedis:${jedisVersion}")
    implementation("com.redislabs:jredistimeseries:${jredistimeseriesVersion}")
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("com.redis.om:redis-om-spring:${redisOmSpringVersion}") {
      constraints { implementation("org.json:json:20230227") }
    }

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.9.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.2")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    integrationTestImplementation("org.springframework.boot:spring-boot-starter-test") {
      // Drop legacy Junit < 5
      exclude(module = "junit")
      exclude(module = "mockito-core")
    }
    integrationTestImplementation("com.ninja-squad:springmockk:3.1.1")
    integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    api("com.github.Cosmo-Tech:cosmotech-api-common:$cosmotechApiCommonVersion") {
      exclude(group = "org.slf4j", module = "slf4j-api")
      because(
          "this depends on org.slf4j:slf4j-api 2.0.3," +
              "which is not backward-compatible with 1.7.x." +
              "See http://www.slf4j.org/faq.html#changesInVersion200")
    }

    api("com.github.Cosmo-Tech:cosmotech-api-azure:$cosmotechApiAzureVersion") {
      exclude(group = "org.slf4j", module = "slf4j-api")
      because(
          "this depends on org.slf4j:slf4j-api 1.8.0-beta4 (pre 2.x)," +
              "which is not backward-compatible with 1.7.x." +
              "See http://www.slf4j.org/faq.html#changesInVersion200")
    }
    implementation(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))
    api("com.azure.spring:azure-spring-boot-starter-storage")
    constraints {
      implementation("redis.clients:jedis:3.9.0") {
        because(
            "3.9.0 version does not contain the UnifiedClient class that we used previously" +
                "and we cannot use 4.X versions as they are not compatible with Spring Boot 2.X")
      }
    }

    implementation("org.springframework.data:spring-data-redis")
  }

  tasks.withType<KotlinCompile> {
    if (openApiDefinitionFile.exists()) {
      dependsOn("openApiGenerate")
    }

    kotlinOptions {
      languageVersion = "1.8"
      freeCompilerArgs = listOf("-Xjsr305=strict")
      jvmTarget = kotlinJvmTarget.toString()
      java { toolchain { languageVersion.set(JavaLanguageVersion.of(kotlinJvmTarget)) } }
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
      templateDir.set("${rootDir}/openapi/templates")
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
              "swaggerAnnotations" to false,
              "useTags" to true,
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
    project.findProperty("identityProvider")?.toString()?.let {
      environment("IDENTITY_PROVIDER", it)
    }

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
    from { image = "eclipse-temurin:17-alpine" }
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
                  shouldRunAfter(subProject.tasks.getByName("detekt"))
                  from(
                      file(
                          "${subProject.projectDir}/build/reports/detekt/${subProject.name}-detekt.$format"))
                  into("${rootProject.buildDir}/reports/detekt/$format")
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

koverMerged {
  enable()
  filters {
    classes {
      excludes +=
          listOf("com.cosmotech.Application*", "com.cosmotech.*.api.*", "com.cosmotech.*.domain.*")
    }
    projects { excludes += listOf("cosmotech-api") }
  }
}

// https://github.com/jk1/Gradle-License-Report/blob/master/README.md
tasks.register<ReportTask>("generateLicenseDoc") {}

tasks.register<CheckLicenseTask>("validateLicense") {
  dependsOn("generateLicenseDoc")
  // Gradle task must be rerun each time to take new allowed-license into account.
  // Due to an issue in the plugin, we must define each module name for null licenses
  // to avoid false negatives in the allowed-license file.
  outputs.upToDateWhen { false }
}

tasks.register("displayLicensesNotAllowed") {
  val notAllowedFile =
      file(
          buildString {
            append(licenseReportDir)
            append("/dependencies-without-allowed-license.json")
          })
  val dependenciesEmptyResumeTemplate = file(licenseEmptyPath)
  if (notAllowedFile.exists() && dependenciesEmptyResumeTemplate.exists()) {
    if (notAllowedFile.readText() != dependenciesEmptyResumeTemplate.readText()) {
      logger.warn("Licenses not allowed:")
      logger.warn(notAllowedFile.readText())
      logger.warn(
          "Please review licenses and add new license check rules in https://github.com/Cosmo-Tech/cosmotech-license")
    } else {
      logger.warn("No error in licences detected!")
    }
  }
}

gradle.buildFinished {
  if (project.properties["skipLicenses"] != "true") {
    val validateLicenseTask = tasks.getByName("validateLicense")
    validateLicenseTask.run {}
    val displayTask = tasks.getByName("displayLicensesNotAllowed")
    displayTask.run {}
  }
}
