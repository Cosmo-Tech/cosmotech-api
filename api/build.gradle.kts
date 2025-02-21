// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import com.google.cloud.tools.jib.gradle.JibTask
import com.rameshkp.openapi.merger.gradle.task.OpenApiMergerTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
  id("com.rameshkp.openapi-merger-gradle-plugin") version "1.0.5"
  id("org.jetbrains.kotlinx.kover")
}

dependencies {
  implementation(projects.cosmotechConnectorApi)
  implementation(projects.cosmotechDatasetApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechWorkspaceApi)
  implementation(projects.cosmotechMetricsService)
  implementation(projects.cosmotechRunApi)
  implementation(projects.cosmotechRunnerApi)

  testImplementation("org.testng:testng:7.8.0")
  testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
}

tasks.getByName<Delete>("clean") { delete("$rootDir/openapi/openapi.yaml") }

tasks.withType<JibTask> {
  // Need to depend on all sub-projects Jar tasks
  val jarTasks =
      parent
          ?.subprojects
          ?.filterNot { it.name == project.name }
          ?.flatMap { it.tasks.withType<Jar>() }
          ?.toList()
  logger.debug("jibTask ${this.name} needs to depend on : $jarTasks")
  if (jarTasks?.isNotEmpty() == true) {
    dependsOn(*jarTasks.toTypedArray())
  }
}

tasks.register<Copy>("copySubProjectsOpenAPIFiles") {
  // By convention, we expect OpenAPI files for sub-projects to be named and placed as follows:
  // <subproject>/src/main/openapi/<subproject>.yaml
  // For example: organization/src/main/openapi/organization.yaml
  val sourcePaths =
      configurations.implementation
          .get()
          .allDependencies
          .withType<ProjectDependency>()
          .asSequence()
          .filter {
            logger.debug("Found project dependency: $it")
            it.name.matches("^cosmotech-[a-zA-Z]+-api$".toRegex())
          }
          .map { it.dependencyProject.projectDir }
          .map { file("${it}/src/main/openapi/${it.relativeTo(rootDir)}.yaml") }
          .filter { it.exists() }
          .map { it.absolutePath }
          .toMutableList()
  // If you need to reference a non-conventional path, feel free to add it below to the
  // sourcePaths local variable, like so: sourcePaths.add("my/path/to/another/openapi.yaml")

  logger.debug("sourcePaths for 'copySubProjectsOpenAPIFiles' task: $sourcePaths")
  if (sourcePaths.isNotEmpty()) {
    from(*sourcePaths.toTypedArray())
    into("${layout.buildDirectory.get()}/tmp/openapi")
  } else {
    logger.warn(
        "Unable to find OpenAPI definitions in project dependencies => 'copySubProjectsOpenAPIFiles' not configured!")
  }
}

openApiMerger {
  openApi {
    openApiVersion.set("3.0.3")
    info {
      title.set("Cosmo Tech Platform API")
      description.set("Cosmo Tech Platform API")
      version.set(project.version.toString())
      contact {
        name.set("Repository")
        email.set("platform@cosmotech.com")
        url.set("https://github.com/Cosmo-Tech/cosmotech-api")
      }
      license {
        name.set("MIT License")
        url.set("https://github.com/Cosmo-Tech/cosmotech-api/blob/main/LICENSE")
      }
    }
  }
}

tasks.getByName<OpenApiMergerTask>("mergeOpenApiFiles") {
  dependsOn("copySubProjectsOpenAPIFiles")
  inputDirectory.set(file("${layout.buildDirectory.get()}/tmp/openapi"))
  outputFileProperty.set(file("$rootDir/openapi/openapi.yaml"))
}

tasks.register<GenerateTask>("openApiTypescriptGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("${layout.buildDirectory.get()}/generated-sources/openapi/typescript")
  generatorName.set("typescript-axios")
  additionalProperties.set(
      mapOf(
          "npmName" to "@cosmotech/api-ts",
      ))
}

tasks.register<Copy>("copyTypescriptGitPushScript") {
  dependsOn("openApiTypescriptGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("${layout.buildDirectory.get()}/generated-sources/openapi/typescript/scripts")
}

tasks.register<Copy>("copyTypescriptLicense") {
  dependsOn("openApiTypescriptGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("${layout.buildDirectory.get()}/generated-sources/openapi/typescript")
}

tasks.register("generateTypescriptClient") {
  dependsOn("copyTypescriptGitPushScript", "copyTypescriptLicense")
}

tasks.register<GenerateTask>("openApiPythonGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("${layout.buildDirectory.get()}/generated-sources/openapi/python")
  generatorName.set("python")
  additionalProperties.set(
      mapOf(
          "projectName" to "cosmotech-api",
          "packageName" to "cosmotech_api",
          "pythonAttrNoneIfUnset" to true))
}

// PROD-14252: temporary fix waiting for upstream resolution of
// https://github.com/OpenAPITools/openapi-generator/pull/20701
tasks.register<Copy>("overwriteGeneratedPythonFile") {
  dependsOn("openApiPythonGenerate")
  from("${rootDir}/scripts/clients/patches/python.yml")
  into("${layout.buildDirectory.get()}/generated-sources/openapi/python/.github/workflows/")
}

tasks.register<Copy>("copyPythonGitPushScript") {
  dependsOn("openApiPythonGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("${layout.buildDirectory.get()}/generated-sources/openapi/python/scripts")
}

tasks.register<Copy>("copyPythonLicense") {
  dependsOn("openApiPythonGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("${layout.buildDirectory.get()}/generated-sources/openapi/python")
}

// PROD-14252: temporary fix waiting for upstream resolution
// of https://github.com/OpenAPITools/openapi-generator/pull/20701
tasks.register("generatePythonClient") { dependsOn("copyPythonGitPushScript", "copyPythonLicense", "overwriteGeneratedPythonFile") }

tasks.register<GenerateTask>("openApiUmlGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$rootDir/openapi/plantuml")
  generatorName.set("plantuml")
}

tasks.register<GenerateTask>("openApiMarkdownGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$rootDir/doc")
  generatorName.set("markdown")
}

tasks.getByName<GenerateTask>("openApiGenerate") { enabled = false }

tasks.getByName<ValidateTask>("openApiValidate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
}

tasks.register("generateClients") {
  dependsOn(
      "generateTypescriptClient",
      "generatePythonClient",
      "openApiUmlGenerate",
      "openApiMarkdownGenerate")
}

tasks.getByName<BootJar>("bootJar") { finalizedBy("generateClients") }

tasks.getByName<Copy>("copyOpenApiYamlToMainResources") { dependsOn("mergeOpenApiFiles") }

tasks.getByName<Copy>("copyOpenApiYamlToTestResources") { dependsOn("mergeOpenApiFiles") }

tasks.withType<GenerateTask> {
  // Force-run all generation tasks, thus bypassing the Gradle Cache
  outputs.upToDateWhen { false }
}

tasks.register<Exec>("rolloutKindDeployment") {
  dependsOn("jib")
  var apiVersion = "latest"
  var namespace = "phoenix"
  var clusterName = "kind-local-k8s-cluster"
  if (project.hasProperty("rollout.apiVersion")) {
    apiVersion = project.property("rollout.apiVersion").toString()
  }
  if (project.hasProperty("rollout.namespace")) {
    namespace = project.property("rollout.namespace").toString()
  }
  if (project.hasProperty("rollout.clusterName")) {
    clusterName = project.property("rollout.clusterName").toString()
  }

  commandLine(
      "kubectl",
      "--context",
      clusterName,
      "-n",
      namespace,
      "rollout",
      "restart",
      "deployment/cosmotech-api-${apiVersion}")
}

tasks.register<GenerateTask>("generateDocumentation") {
    group = "documentation"
    description = "Generates adoc file containing API documentation"
    inputSpec.set("${rootDir}/openapi/openapi.yaml")
    outputDir.set("${rootDir}/doc")
    generatorName.set("asciidoc")
    additionalProperties.set(
        mapOf(
            "appName" to "Cosmo Tech API",
            "appDescription" to "Cosmo Tech API Description",
            "disallowAdditionalPropertiesIfNotPresent" to false,
            "infoEmail" to "platform@cosmotech.com",
            "snippetDir" to "${rootDir}/doc/generated-snippets/",
            "infoUrl" to "https://github.com/Cosmo-Tech/cosmotech-api")
    )
}