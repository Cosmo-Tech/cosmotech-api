// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.*
import com.github.jk1.license.task.ReportTask
import com.google.cloud.tools.jib.api.buildplan.ImageFormat.OCI
import com.google.cloud.tools.jib.gradle.JibExtension
import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.apache.tools.ant.filters.ReplaceTokens
import org.cyclonedx.gradle.CycloneDxTask
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
  val kotlinVersion = "2.0.21"
  kotlin("jvm") version kotlinVersion
  kotlin("plugin.spring") version kotlinVersion apply false
  id("pl.allegro.tech.build.axion-release") version "1.18.18"
  id("com.diffplug.spotless") version "7.0.3"
  id("org.springframework.boot") version "3.4.4" apply false
  id("project-report")
  id("org.owasp.dependencycheck") version "12.1.0"
  id("com.github.jk1.dependency-license-report") version "2.9"
  id("org.jetbrains.kotlinx.kover") version "0.9.1"
  id("io.gitlab.arturbosch.detekt") version "1.23.8"
  id("org.openapi.generator") version "7.13.0" apply false
  id("com.google.cloud.tools.jib") version "3.4.5" apply false
  id("org.cyclonedx.bom") version "2.3.1"
}

scmVersion { tag { prefix.set("") } }

group = "com.cosmotech"

version = scmVersion.version

// Dependencies version
val jacksonVersion = "2.18.3"
val springWebVersion = "6.2.9"
val bouncyCastleJdk18Version = "1.81"
val springBootVersion = "3.4.4"
val springSecurityJwtVersion = "1.1.1.RELEASE"
val springOauthAutoConfigureVersion = "2.6.8"
val kotlinJvmTarget = 21
val cosmotechApiCommonVersion = "2.1.1-SNAPSHOT"
val redisOmSpringVersion = "0.9.7"
val kotlinCoroutinesVersion = "1.10.2"
val oktaSpringBootVersion = "3.0.7"
val springDocVersion = "2.8.8"
val swaggerParserVersion = "2.1.31"
val commonsCsvVersion = "1.14.0"
val apiValidationVersion = "3.0.2"
val kubernetesClientVersion = "22.0.0"
val orgJsonVersion = "20240303"
val jacksonModuleKotlinVersion = "2.18.3"
val testNgVersion = "7.8.0"
val testContainersRedisVersion = "1.6.4"
val testContainersPostgreSQLVersion = "1.20.6"
val testContainersLocalStackVersion = "1.20.6"
val commonCompressVersion = "1.27.1"
val awsSpringVersion = "3.3.0"

// Checks
val detektVersion = "1.23.8"

// Tests
val jUnitBomVersion = "5.12.2"
val mockkVersion = "1.14.0"
val awaitilityKVersion = "4.2.0"
val springMockkVersion = "4.0.2"

val configBuildDir = "${layout.buildDirectory.get()}/config"

mkdir(configBuildDir)

dependencyCheck {
  // Configure dependency check plugin. It checks for publicly disclosed
  // vulnerabilities in project dependencies. To use it, you need to have an
  // API key from the NVD (National Vulnerability Database), pass it by setting
  // the environment variable NVD_API_key (See project README.md: Static Code
  // Analysis -> Vulnerability report).
  nvd { apiKey = System.getenv("NVD_API_key") }
}

licenseReport {
  allowedLicensesFile =
      "https://raw.githubusercontent.com/Cosmo-Tech/cosmotech-license/refs/heads/main/config/allowed-licenses.json"
  val bundle =
      "https://raw.githubusercontent.com/Cosmo-Tech/cosmotech-license/refs/heads/main/config/license-normalizer-bundle.json"

  renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("index.html"))
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
  apply(plugin = "org.cyclonedx.bom")

  version = rootProject.scmVersion.version ?: error("Root project did not configure scmVersion!")

  java {
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain { languageVersion.set(JavaLanguageVersion.of(kotlinJvmTarget)) }
  }
  configurations { all { resolutionStrategy { force("com.redis.om:redis-om-spring:0.9.10") } } }

  repositories {
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

  tasks.cyclonedxBom {
    includeConfigs = listOf("runtimeClasspath")
    outputFormat = "xml" // by default it would also generate json
    projectType = "application"
    outputName = "cosmotech-api-bom"
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
      licenseHeader(licenseHeaderComment, "(import |// no-import)")
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
    // https://youtrack.jetbrains.com/issue/KT-71057/POM-file-unusable-after-upgrading-to-2.0.20-from-2.0.10
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))
    detekt("io.gitlab.arturbosch.detekt:detekt-cli:$detektVersion")
    detekt("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")

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
        implementation("io.undertow:undertow-core:2.3.18.Final")
      }
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")
    // https://mvnrepository.com/artifact/jakarta.validation/jakarta.validation-api
    implementation("jakarta.validation:jakarta.validation-api:$apiValidationVersion")
    implementation("io.kubernetes:client-java:${kubernetesClientVersion}")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${springDocVersion}")
    implementation("io.swagger.parser.v3:swagger-parser-v3:${swaggerParserVersion}")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(
        "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:${springOauthAutoConfigureVersion}") {
          constraints {
            implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
            implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
            implementation("org.springframework:spring-web:$springWebVersion")
            implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
            implementation(
                "org.springframework.security:spring-security-jwt:${springSecurityJwtVersion}") {
                  exclude(group = "org.bouncycastle", module = "bcpkix-jdk15on")
                  constraints {
                    implementation("org.bouncycastle:bcpkix-jdk18on:${bouncyCastleJdk18Version}")
                  }
                }
          }
        }
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("com.okta.spring:okta-spring-boot-starter:${oktaSpringBootVersion}")

    implementation("org.apache.commons:commons-csv:$commonsCsvVersion")
    implementation("com.redis.om:redis-om-spring:${redisOmSpringVersion}")
    implementation("org.springframework.data:spring-data-redis")
    implementation("org.springframework:spring-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.apache.commons:commons-compress:$commonCompressVersion")

    implementation("org.json:json:$orgJsonVersion")

    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:$awsSpringVersion"))
    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3:$awsSpringVersion")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:$jUnitBomVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.awaitility:awaitility-kotlin:$awaitilityKVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinCoroutinesVersion")
    testImplementation("org.testng:testng:$testNgVersion")
    testImplementation(
        "com.redis.testcontainers:testcontainers-redis-junit:$testContainersRedisVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersPostgreSQLVersion")
    testImplementation("org.testcontainers:localstack:$testContainersLocalStackVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    integrationTestImplementation("org.springframework.boot:spring-boot-starter-test") {
      // Drop legacy Junit < 5
      exclude(module = "junit")
      exclude(module = "mockito-core")
    }
    integrationTestImplementation("com.ninja-squad:springmockk:$springMockkVersion")
    // developmentOnly("org.springframework.boot:spring-boot-devtools")
    integrationTestImplementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinCoroutinesVersion")
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
    group = "documentation"
    description = "Copy test generated code snippets to \$rootDir/doc/generated-snippets/"
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
      // Templates here were enabled due to an open PR in OpenAPITools/openapi-generator
      // https://github.com/OpenAPITools/openapi-generator/pull/21994
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
    group = "openapi"
    description = "Copy openapi.yaml files to \$buildDir/resources/main/static/openapi.yaml"
    from(openApiFileDefinition)
    into("${layout.buildDirectory.get()}/resources/main/static")
    rename { if (it != "openapi.yaml") "openapi.yaml" else it }
  }

  tasks.register<Copy>("copyOpenApiYamlToTestResources") {
    group = "openapi"
    description = "Copy test openapi.yaml files to \$buildDir/resources/test/static/openapi.yaml"
    from(openApiFileDefinition)
    into("${layout.buildDirectory.get()}/resources/test/static")
    rename { if (it != "openapi.yaml") "openapi.yaml" else it }
  }

  var fullVersion = project.version.toString()
  var buildVersion = ""
  if (!rootProject.scmVersion.scmPosition.revision.isNullOrEmpty()) {
    buildVersion = rootProject.scmVersion.scmPosition.revision.substring(0, 8)
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
    group = "information"
    description = "Copy test about.json file to \$rootDir/api/src/main/resources/about.json"
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

tasks.getByName("spotlessJava") {
  dependsOn(":cosmotech-api:openApiMarkdownGenerate", ":cosmotech-api:openApiUmlGenerate")
}

tasks.getByName("spotlessKotlin") {
  dependsOn(":cosmotech-api:openApiMarkdownGenerate", ":cosmotech-api:openApiUmlGenerate")
}

tasks.getByName("spotlessKotlinGradle") {
  dependsOn(":cosmotech-api:openApiMarkdownGenerate", ":cosmotech-api:openApiUmlGenerate")
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
                  group = "detekt"
                  description =
                      "Copy sub-projects detekt reports to \$projectDir/build/reports/detekt/\$format"
                  dependsOn("spotlessKotlin", "spotlessKotlinGradle", "spotlessJava")
                  from(
                      file(
                          "${subProject.projectDir}/build/reports/detekt/${subProject.name}-detekt.$format"))
                  into(
                      "${subProject.parent!!.layout.projectDirectory}/build/reports/detekt/$format")
                }
        subProject.tasks.getByName("detekt") { finalizedBy(copyTask) }
        subProject.tasks.withType<CycloneDxTask> { finalizedBy(copyTask) }
        copyTask
      }
    }

tasks.getByName("detekt") { shouldRunAfter(*copySubProjectsDetektReportsTasks.toTypedArray()) }

extensions.configure<KoverProjectExtension>("kover") {
  reports {
    filters {
      excludes {
        projects { cosmotechApi }
        classes("com.cosmotech.Application*")
        classes("com.cosmotech.*.api.*")
        classes("com.cosmotech.*.domain.*")
      }
    }
  }
}

kover {
  reports {
    total {
      // reports configs for XML, HTML, verify reports
    }
  }
}

// https://github.com/jk1/Gradle-License-Report/blob/master/README.md
tasks.register<ReportTask>("generateLicenseDoc") {
  group = "license"
  description = "Generate Licenses report"
}

tasks.register("generateAllReports") {
  group = "reporting"
  description =
      """Generates all available reports (test, coverage, dependencies, licenses, detekt)
    |/!\ Warning: Please do not run this task locally, tests are really resource consuming
  """
          .trimMargin()
  dependsOn(
      // Test reports, need to gather them first
      // Please do not run this task locally, tests are really resource consuming
      "test",
      "integrationTest",
      // Coverage reports
      "koverHtmlReport",
      // Dependency reports
      "htmlDependencyReport",
      // License reports
      "generateLicenseReport",
      // Code analysis reports
      "detekt")
  doLast {
    // Create reports directory if it doesn't exist
    val reportsDir = layout.buildDirectory.get().dir("reports").asFile
    reportsDir.mkdirs()
    // Copy the template file to the reports directory
    copy {
      from("api/src/main/resources") {
        include("reports-index.html")
        include("reports-style.css")
      }
      into(reportsDir)
    }
  }
}
