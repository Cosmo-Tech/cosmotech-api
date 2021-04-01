import com.google.cloud.tools.jib.gradle.JibTask
import com.rameshkp.openapi.merger.gradle.task.OpenApiMergerTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins { id("com.rameshkp.openapi-merger-gradle-plugin") version "1.0.3" }

dependencies {
  api(project(":cosmotech-api-common"))
  implementation(project(":cosmotech-organization-api"))
  implementation(project(":cosmotech-user-api"))
  implementation(project(":cosmotech-connector-api"))
  implementation(project(":cosmotech-dataset-api"))
}

tasks.getByName<Delete>("clean") {
  delete("$rootDir/openapi/openapi.yaml", "$rootDir/openapi/plantuml")
}

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
      parent
          ?.subprojects
          ?.map {
            file("${it.projectDir}/src/main/openapi/${it.projectDir.relativeTo(rootDir)}s.yaml")
          }
          ?.filter { it.exists() }
          ?.map { it.absolutePath }
          ?.toMutableList()
  // If you need to reference a non-conventional path, feel free to add it below to the
  // sourcePaths local variable, like so: sourcePaths.add("my/path/to/another/openapi.yaml")

  if (sourcePaths?.isNotEmpty() == true) {
    from(*sourcePaths.toTypedArray())
    into("$buildDir/tmp/openapi")
  } else {
    logger.warn(
        "Unable to find OpenAPI defitions in parent sub-projects => 'copySubProjectsOpenAPIFiles' not configured ! ")
  }
}

openApiMerger {
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
          "moduleName" to "CosmotechApi"))
}

tasks.register<Copy>("copyJSGitPushScript") {
  dependsOn("openApiJSGenerate")
  from("${rootDir}/scripts/git_push.sh")
  into("$buildDir/generated-sources/javascript")
}

tasks.register("generateJSClient") { dependsOn("copyJSGitPushScript") }

tasks.register<GenerateTask>("openApiPythonGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/python")
  generatorName.set("python")
  additionalProperties.set(
      mapOf("projectName" to "cosmotech-api", "packageName" to "cosmotech_api"))
}

tasks.register<Copy>("copyPythonGitPushScript") {
  dependsOn("openApiPythonGenerate")
  from("${rootDir}/scripts/git_push.sh")
  into("$buildDir/generated-sources/python")
}

tasks.register<GenerateTask>("openApiUmlGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$rootDir/openapi/plantuml")
  generatorName.set("plantuml")
}

tasks.register("generatePythonClient") { dependsOn("copyPythonGitPushScript") }

tasks.getByName<GenerateTask>("openApiGenerate") { enabled = false }

tasks.getByName<ValidateTask>("openApiValidate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
}

tasks.register("generateClients") {
  dependsOn("generateJSClient", "generatePythonClient", "openApiUmlGenerate")
}

tasks.getByName<BootJar>("bootJar") { finalizedBy("generateClients") }

tasks.getByName("convertOpenAPIYaml2Json") { dependsOn("mergeOpenApiFiles") }
