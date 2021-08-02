import com.google.cloud.tools.jib.gradle.JibTask
import com.rameshkp.openapi.merger.gradle.task.OpenApiMergerTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins { id("com.rameshkp.openapi-merger-gradle-plugin") version "1.0.4" }

dependencies {
  api(projects.cosmotechApiCommon)
  implementation("io.argoproj.workflow:argo-client-java:v3.0.1")
  implementation(projects.cosmotechConnectorApi)
  implementation(projects.cosmotechDatasetApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechScenarioApi)
  implementation(projects.cosmotechScenariorunApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechUserApi)
  implementation(projects.cosmotechWorkspaceApi)
}

tasks.getByName<Delete>("clean") { delete("$rootDir/openapi/openapi.yaml") }

tasks.withType<JibTask> {
  // Need to depend on all sub-projects Jar tasks
  val jarTasks =
      parent
          ?.subprojects
          ?.filter { it.name != project.name && it.name != "cosmotech-api-common" }
          ?.flatMap { it.tasks.withType<Jar>() }
          ?.toList()
  logger.debug("jibTask ${this.name} needs to depend on : $jarTasks")
  if (jarTasks?.isNotEmpty() == true) {
    dependsOn(*jarTasks.toTypedArray())
  }
}

tasks.register<Copy>("copySubProjectsOpenAPIFiles") {
  // By convention, we expect OpenAPI files for sub-projects to be named and placed as follows:
  // <subproject>/src/main/openapi/<subproject>s.yaml
  // For example: organization/src/main/openapi/organizations.yaml
  val sourcePaths =
      configurations
          .implementation
          .get()
          .allDependencies
          .withType<ProjectDependency>()
          .asSequence()
          .filter {
            logger.debug("Found project dependency: $it")
            it.name.matches("^cosmotech-[a-zA-Z]+-api$".toRegex())
          }
          .map { it.dependencyProject.projectDir }
          .map { file("${it}/src/main/openapi/${it.relativeTo(rootDir)}s.yaml") }
          .filter { it.exists() }
          .map { it.absolutePath }
          .toMutableList()
  // If you need to reference a non-conventional path, feel free to add it below to the
  // sourcePaths local variable, like so: sourcePaths.add("my/path/to/another/openapi.yaml")

  logger.debug("sourcePaths for 'copySubProjectsOpenAPIFiles' task: $sourcePaths")
  if (sourcePaths.isNotEmpty()) {
    from(*sourcePaths.toTypedArray())
    into("$buildDir/tmp/openapi")
  } else {
    logger.warn(
        "Unable to find OpenAPI definitions in project dependencies => 'copySubProjectsOpenAPIFiles' not configured!")
  }
}

openApiMerger {
  openApi {
    openApiVersion.set("3.0.3")
    info {
      title.set("Cosmo Tech Plaform API")
      description.set("Cosmo Tech Platform API")
      version.set(project.version.toString())
      //      termsOfService.set("http://openapimerger.com/terms-of-service")
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
    servers {
      register("development") {
        url.set("https://dev.api.cosmotech.com")
        description.set("Development")
      }
      register("production") {
        url.set("https://api.cosmotech.com")
        description.set("Production")
      }
    }
  }
}

tasks.getByName<OpenApiMergerTask>("mergeOpenApiFiles") {
  dependsOn("copySubProjectsOpenAPIFiles")
  inputDirectory.set(file("$buildDir/tmp/openapi"))
  outputFileProperty.set(file("$rootDir/openapi/openapi.yaml"))
}

tasks.register<GenerateTask>("openApiJSGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/javascript")
  generatorName.set("javascript")
  additionalProperties.set(
      mapOf(
          "projectName" to "@cosmotech/api",
          "projectDescription" to "Cosmo Tech Platform API client",
          "moduleName" to "CosmotechApi",
          "usePromises" to "true"))
}

tasks.register<Copy>("copyJSGitPushScript") {
  dependsOn("openApiJSGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/javascript/scripts")
}

tasks.register<Copy>("copyJSLicense") {
  dependsOn("openApiJSGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/javascript")
}

tasks.register("generateJSClient") { dependsOn("copyJSGitPushScript", "copyJSLicense") }

tasks.register<GenerateTask>("openApiTypescriptGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/typescript")
  generatorName.set("typescript-axios")
  additionalProperties.set(
      mapOf(
          "npmName" to "@cosmotech/api-ts",
        )
      )
}

tasks.register<Copy>("copyTypescriptGitPushScript") {
  dependsOn("openApiTypescriptGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/typescript/scripts")
}

tasks.register<Copy>("copyTypescriptLicense") {
  dependsOn("openApiTypescriptGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/typescript")
}

tasks.register("generateTypescriptClient") { dependsOn("copyTypescriptGitPushScript", "copyTypescriptLicense") }

tasks.register<GenerateTask>("openApiPythonGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/python")
  generatorName.set("python")
  additionalProperties.set(
      mapOf(
          "projectName" to "cosmotech-api",
          "packageName" to "cosmotech_api",
          "pythonAttrNoneIfUnset" to true))
}

tasks.register<Copy>("copyPythonGitPushScript") {
  dependsOn("openApiPythonGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/python/scripts")
}

tasks.register<Copy>("copyPythonLicense") {
  dependsOn("openApiPythonGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/python")
}

tasks.register("generatePythonClient") { dependsOn("copyPythonGitPushScript", "copyPythonLicense") }

tasks.register<GenerateTask>("openApiJavaGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/java")
  generatorName.set("java")
  additionalProperties.set(
      mapOf(
          "apiPackage" to "com.cosmotech.client.api",
          "artifactDescription" to "Cosmo Tech API Java Client",
          "artifactId" to "cosmotech-api-java-client",
          "artifactUrl" to "https://github.com/Cosmo-Tech/cosmotech-api-java-client",
          "developerEmail" to "team.engineering@cosmotech.com",
          "developerName" to "Cosmo Tech",
          "developerOrganization" to "Cosmo Tech",
          "developerOrganizationUrl" to "https://cosmotech.com/",
          "groupId" to "com.cosmotech",
          "invokerPackage" to "com.cosmotech.client",
          "licenseName" to "MIT",
          "licenseUrl" to
              "https://github.com/Cosmo-Tech/cosmotech-api-java-client/blob/master/LICENSE",
          "modelPackage" to "com.cosmotech.client.model",
          "scmConnection" to "scm:git:git@github.com:Cosmo-Tech/cosmotech-api-java-client",
          "scmDeveloperConnection" to "scm:git:git@github.com:Cosmo-Tech/cosmotech-api-java-client",
          "scmUrl" to "https://github.com/Cosmo-Tech/cosmotech-api-java-client"))
}

tasks.register<Copy>("copyJavaGitPushScript") {
  dependsOn("openApiJavaGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/java/scripts")
}

tasks.register<Copy>("copyJavaLicense") {
  dependsOn("openApiJavaGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/java")
}

tasks.register("generateJavaClient") { dependsOn("copyJavaGitPushScript", "copyJavaLicense") }

tasks.register<GenerateTask>("openApiCSharpGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/csharp")
  generatorName.set("csharp-netcore")
  additionalProperties.set(mapOf("packageName" to "Com.Cosmotech"))
}

tasks.register<Copy>("copyCSharpGitPushScript") {
  dependsOn("openApiCSharpGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/csharp/scripts")
}

tasks.register<Copy>("copyCSharpLicense") {
  dependsOn("openApiCSharpGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/csharp")
}

tasks.register("generateCSharpClient") { dependsOn("copyCSharpGitPushScript", "copyCSharpLicense") }

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
      "generateJSClient",
      "generatePythonClient",
      "generateJavaClient",
      "generateCSharpClient",
      "openApiUmlGenerate",
      "openApiMarkdownGenerate")
}

tasks.getByName<BootJar>("bootJar") { finalizedBy("generateClients") }

tasks.getByName<Copy>("copyOpenApiYamlToMainResources") { dependsOn("mergeOpenApiFiles") }

tasks.getByName<Copy>("copyOpenApiYamlToTestResources") { dependsOn("mergeOpenApiFiles") }
