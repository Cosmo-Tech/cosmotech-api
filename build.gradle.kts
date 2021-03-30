import com.diffplug.gradle.spotless.SpotlessExtension
import com.rameshkp.openapi.merger.gradle.task.OpenApiMergerTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
  val kotlinVersion = "1.4.31"
  kotlin("jvm") version kotlinVersion
  kotlin("plugin.spring") version kotlinVersion

  id("com.diffplug.spotless") version "5.11.1"

  id("org.springframework.boot") version "2.4.4"
  id("io.spring.dependency-management") version "1.0.11.RELEASE"

  id("org.openapi.generator") version "5.1.0"

  id("com.rameshkp.openapi-merger-gradle-plugin") version "1.0.3"
}

dependencies {
  api(project(":cosmotech-api-common"))
  implementation(project(":cosmotech-organization-api"))
  implementation(project(":cosmotech-user-api"))
  implementation(project(":cosmotech-connector-api"))
}

allprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jetbrains.kotlin.plugin.spring")
  apply(plugin = "org.springframework.boot")
  apply(plugin = "io.spring.dependency-management")

  group = "com.cosmotech"
  version = "0.0.1-SNAPSHOT"

  java { toolchain { languageVersion.set(JavaLanguageVersion.of(16)) } }

  repositories { mavenCentral() }

  dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("javax.validation:validation-api:2.0.1.Final")
    implementation("io.swagger:swagger-annotations:1.6.2")

    val springfoxVersion = "3.0.0"
    implementation("io.springfox:springfox-boot-starter:${springfoxVersion}")
    implementation("io.springfox:springfox-swagger-ui:${springfoxVersion}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
  }

  configure<SpotlessExtension> {
    java { googleJavaFormat() }
    kotlin { ktfmt() }
    kotlinGradle { ktfmt() }
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions {
      freeCompilerArgs = listOf("-Xjsr305=strict")
      jvmTarget = "15"
    }
  }

  tasks.withType<Test> { useJUnitPlatform() }

  tasks.getByName<Jar>("jar") { enabled = true }

  tasks.getByName<BootJar>("bootJar") { classifier = "uberjar" }

  if (project.name != "cosmotech-api-common") {
    tasks.withType<KotlinCompile> { dependsOn("openApiGenerate") }
  }
}

tasks.register<Copy>("copySubProjectsOpenAPIFiles") {
  from(
      "organization/src/main/openapi/organizations.yaml",
      "user/src/main/openapi/users.yaml",
      "connector/src/main/openapi/connectors.yaml")
  into("$buildDir/tmp/openapi")
}

openApiMerger {
  inputDirectory.set(file("$buildDir/tmp/openapi"))
  output {
    directory.set(file("$rootDir/openapi"))
    fileName.set("openapi")
    fileExtension.set("yaml")
  }
  openApi {
    openApiVersion.set("3.0.3")
    info {
      title.set("Cosmo Tech Plaform API")
      description.set("Cosmo Tech Platform API")
      version.set(project.version.toString())
      //			termsOfService.set("http://openapimerger.com/terms-of-service")
      //			contact {
      //				name.set("OpenApiMerger Team")
      //				email.set("openapi@sample.com")
      //				url.set("http://openapimerger.com")
      //			}
      //			license {
      //				name.set("Apache License v2.0")
      //				url.set("http://apache.org/v2")
      //			}
    }
    //		externalDocs {
    //			description.set("External docs description")
    //			url.set("http://external-docs.com/uri")
    //		}
  }
}

tasks.getByName<OpenApiMergerTask>("mergeOpenApiFiles") { dependsOn("copySubProjectsOpenAPIFiles") }

tasks.register<GenerateTask>("openApiJSGenerate") {
  dependsOn("mergeOpenApiFiles")
  input = "${projectDir}/openapi/openapi.yaml"
  outputDir.set("$buildDir/generated-sources/javascript")
  generatorName.set("javascript")
  additionalProperties.set(
      mapOf(
          "projectName" to "@cosmotech/api",
          "projectDescription" to "Cosmo Tech Platform API client",
          "moduleName" to "CosmotechApi"))
}

tasks.register<Copy>("copyJSGitPushScript") {
  dependsOn("openApiJSGenerate")
  from("${projectDir}/scripts/git_push.sh")
  into("$buildDir/generated-sources/javascript")
}

tasks.register("generateJSClient") { dependsOn("copyJSGitPushScript") }

tasks.register<GenerateTask>("openApiPythonGenerate") {
  dependsOn("mergeOpenApiFiles")
  input = "${projectDir}/openapi/openapi.yaml"
  outputDir.set("$buildDir/generated-sources/python")
  generatorName.set("python")
  additionalProperties.set(
      mapOf("projectName" to "cosmotech-api", "packageName" to "cosmotech_api"))
}

tasks.register<Copy>("copyPythonGitPushScript") {
  dependsOn("openApiPythonGenerate")
  from("${projectDir}/scripts/git_push.sh")
  into("$buildDir/generated-sources/python")
}

tasks.register<GenerateTask>("openApiUmlGenerate") {
  dependsOn("mergeOpenApiFiles")
  input = "${projectDir}/openapi/openapi.yaml"
  outputDir.set("$projectDir/openapi/plantuml")
  generatorName.set("plantuml")
}

tasks.register("generatePythonClient") { dependsOn("copyPythonGitPushScript") }

tasks.getByName<GenerateTask>("openApiGenerate") { enabled = false }

tasks.register("generateClients") { dependsOn("generateJSClient", "generatePythonClient", "openApiUmlGenerate") }

tasks.getByName<BootJar>("bootJar") { finalizedBy("generateClients") }
