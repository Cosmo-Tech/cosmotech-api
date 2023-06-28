// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import com.fasterxml.jackson.core.JsonProcessingException
import com.google.cloud.tools.jib.gradle.JibTask
import io.swagger.models.ExternalDocs
import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.callbacks.Callback
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.links.Link
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask
import org.slf4j.LoggerFactory
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.util.*


plugins {
//  id("com.rameshkp.openapi-merger-gradle-plugin") version "1.0.5"
  id("org.jetbrains.kotlinx.kover")
}
// add custom jar containing Gradle plugin locally in gradle folder




dependencies {
  implementation(projects.cosmotechConnectorApi)
  implementation(projects.cosmotechDatasetApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechScenarioApi)
  implementation(projects.cosmotechScenariorunApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechWorkspaceApi)
  implementation(projects.cosmotechTwingraphApi)
  implementation(projects.cosmotechMetricsService)
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
    into("$buildDir/tmp/openapi")
  } else {
    logger.warn(
        "Unable to find OpenAPI definitions in project dependencies => 'copySubProjectsOpenAPIFiles' not configured!")
  }
}

//openApiMerger {
//  openApi {
//    openApiVersion.set("3.0.3")
//    info {
//      title.set("Cosmo Tech Platform API")
//      description.set("Cosmo Tech Platform API")
//      version.set(project.version.toString())
//      //      termsOfService.set("http://openapimerger.com/terms-of-service")
//      contact {
//        name.set("Repository")
//        email.set("platform@cosmotech.com")
//        url.set("https://github.com/Cosmo-Tech/cosmotech-api")
//      }
//      license {
//        name.set("MIT License")
//        url.set("https://github.com/Cosmo-Tech/cosmotech-api/blob/main/LICENSE")
//      }
//    }
//    servers {
//      register("development") {
//        url.set("https://dev.api.cosmotech.com")
//        description.set("Development")
//      }
//      register("staging") {
//        url.set("https://staging.api.cosmotech.com")
//        description.set("Staging")
//      }
//      register("production") {
//        url.set("https://api.cosmotech.com")
//        description.set("Production")
//      }
//    }
//  }
//}

//tasks.getByName<OpenApiMergerTask>("mergeOpenApiFiles") {
//  dependsOn("copySubProjectsOpenAPIFiles")
//  inputDirectory.set(file("$buildDir/tmp/openapi"))
//  outputFileProperty.set(file("$rootDir/openapi/openapi.yaml"))
//}

tasks.register<GenerateTask>("openApiJSGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/openapi/javascript")
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
  into("$buildDir/generated-sources/openapi/javascript/scripts")
}

tasks.register<Copy>("copyJSLicense") {
  dependsOn("openApiJSGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/openapi/javascript")
}

tasks.register("generateJSClient") { dependsOn("copyJSGitPushScript", "copyJSLicense") }

tasks.register<GenerateTask>("openApiTypescriptGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/openapi/typescript")
  generatorName.set("typescript-axios")
  additionalProperties.set(
      mapOf(
          "npmName" to "@cosmotech/api-ts",
      ))
}

tasks.register<Copy>("copyTypescriptGitPushScript") {
  dependsOn("openApiTypescriptGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/openapi/typescript/scripts")
}

tasks.register<Copy>("copyTypescriptLicense") {
  dependsOn("openApiTypescriptGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/openapi/typescript")
}

tasks.register("generateTypescriptClient") {
  dependsOn("copyTypescriptGitPushScript", "copyTypescriptLicense")
}

tasks.register<GenerateTask>("openApiPythonGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/openapi/python")
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
  into("$buildDir/generated-sources/openapi/python/scripts")
}

tasks.register<Copy>("copyPythonLicense") {
  dependsOn("openApiPythonGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/openapi/python")
}

tasks.register("generatePythonClient") { dependsOn("copyPythonGitPushScript", "copyPythonLicense") }

tasks.register<GenerateTask>("openApiJavaGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/openapi/java")
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
  into("$buildDir/generated-sources/openapi/java/scripts")
}

tasks.register<Copy>("copyJavaLicense") {
  dependsOn("openApiJavaGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/openapi/java")
}

tasks.register("generateJavaClient") { dependsOn("copyJavaGitPushScript", "copyJavaLicense") }

tasks.register<GenerateTask>("openApiCSharpGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/openapi/csharp")
  generatorName.set("csharp-netcore")
  additionalProperties.set(mapOf("packageName" to "Com.Cosmotech"))
}

tasks.register<Copy>("copyCSharpGitPushScript") {
  dependsOn("openApiCSharpGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/openapi/csharp/scripts")
}

tasks.register<Copy>("copyCSharpLicense") {
  dependsOn("openApiCSharpGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/openapi/csharp")
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
      "generateTypescriptClient",
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

tasks.register<GreetingTask>("mergeOpenApiFiles") {
  inputDirectory.set(file("$buildDir/tmp/openapi"))
  outputFileProperty.set(file("$rootDir/openapi/openapi.yaml"))
}




abstract class GreetingTask : DefaultTask() {
    @get:InputDirectory
    val inputDirectory: DirectoryProperty = project.objects.directoryProperty()
    @get:OutputFile
    val outputFileProperty: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun greet() {
        val log = LoggerFactory.getLogger(javaClass)
        val outputFile = outputFileProperty.asFile.get()
        val openApi = OpenApi()
        val validFileExtension = listOf("yaml", "json", "yml")
        val parseOptions = ParseOptions()
        val openApiMerger = OpenApiMerger()

        inputDirectory.get().asFile.walk().filter {
            validFileExtension.contains(it.extension)
        }.sortedBy {
            it
        }.forEach {
            println("file: ${it.absolutePath}")
            val openAPI = OpenAPIV3Parser().read(it.absolutePath, null, parseOptions)
            openApiMerger.merge(openAPI)
        }
// Convert the server object to open api server objects
        val servers  = openApi!!.servers.map {
            val s = Server()
            s.url = it.url
            s.description = it.description
            s
        }

        // Set the relevant data for merged files
        val merged = openApiMerger.get()
        merged?.run {
            log.debug("Constructing the OpenApi model")
            // Set the openapi model object values to merged file
            openApi.let { openApiModel ->
                openapi = openApiModel.version
                // Set the info object
                info = openApiModel.info?.let { infoModelObj ->
                    val info = Info()
                    info.title = infoModelObj.title
                    info.version = infoModelObj.version
                    info.description = infoModelObj.description
                    info.termsOfService = infoModelObj.termsOfService

                    // set contact
                    info.contact = infoModelObj.contact?.let { contactModelObj ->
                        val contact = Contact()
                        contact.url = contactModelObj.url
                        contact.email = contactModelObj.email
                        contact.name = contactModelObj.name
                        contact
                    }

                    // Set license
                    info.license = infoModelObj.license?.let { licenseModelObj ->
                        val license = License()
                        license.url = licenseModelObj.url
                        license.name = licenseModelObj.name
                        license
                    }
                    info
                }
                externalDocs = openApiModel.externalDocs?.let { externalDocsModelObj ->
                    val externalDocs = ExternalDocumentation()
                    externalDocs.url = externalDocsModelObj.url
                    externalDocs.description = externalDocsModelObj.description
                    externalDocs
                }

                if (servers.isNotEmpty()) {
                    this.servers = servers
                }
            }

            // Get convert the object and get it as string
            val out = when(outputFile.extension) {
                "json" -> OpenAPIConverter.toJson(this)
                "yaml", "yml" -> OpenAPIConverter.toYaml(this)
                else -> ""
            }

            // Write it the output file
            log.debug("Writing the merged output file {}", outputFile.absolutePath)
            outputFile.writeText(out, Charsets.UTF_8)
        }


    }

}



data class OpenApi(var version: String? = null, var info: Info? = null,
                   var externalDocs: ExternalDocs? = null, var servers: List<Server> = emptyList())


object OpenAPIConverter {
    /**
     * Provided an open api object this method converts it to a json string
     * @param openAPI object to convert
     * @return json string of the object
     * @throws JsonProcessingException when json parsing fails
     */
    @Throws(JsonProcessingException::class)
    fun toJson(openAPI: OpenAPI?): String {
        return Json.pretty().writeValueAsString(openAPI)
    }

    /**
     * Provided an open api object this method converts it to a json string
     * @param openAPI object to convert
     * @return yaml string of the object
     * @throws JsonProcessingException when yaml parsing fails
     */
    @Throws(JsonProcessingException::class)
    fun toYaml(openAPI: OpenAPI?): String {
        return Yaml.pretty().writeValueAsString(openAPI)
    }
}


interface Mergeable<T> {
    fun merge(from: T?)
    fun get(): T?
}

class OpenApiMerger: Mergeable<OpenAPI> {
    private val log = LoggerFactory.getLogger(javaClass)
    private val openAPI = OpenAPI()

    private val serversMerger = ListMerger<Server>()
    private val tagsMerger = ListMerger<Tag>()
    private val securityMerger = ListMerger<SecurityRequirement>()
    private val pathsMerger = PathsMerger()
    private val componentsMerger = ComponentsMerger()

    override fun merge(from: OpenAPI?) {
        from?.run {
            // Merge the server
            log.info("Merging servers")
            serversMerger.merge(this.servers)

            // Merge the paths
            log.info("Merging paths")
            pathsMerger.merge(this.paths)

            // Merge the components
            log.info("Merging components")
            componentsMerger.merge(this.components)

            // Merge security
            log.info("Merging Security")
            securityMerger.merge(this.security)

            // Merge tags
            log.info("Merging tags")
            tagsMerger.merge(this.tags)
        }
    }

    override fun get(): OpenAPI? {
        openAPI.servers = serversMerger.get()
        openAPI.paths = pathsMerger.get()
        openAPI.components = componentsMerger.get()
        openAPI.security = securityMerger.get()
        openAPI.tags = tagsMerger.get()
        return openAPI
    }
}


class PathItemMerger(private val path: String, private val pathItem: PathItem): Mergeable<PathItem> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun merge(from: PathItem?) {
        from?.apply {
            pathItem.setIfMissing(
                "description",
                { pathItem -> pathItem.description },
                { pathItem -> pathItem.description = description })
            pathItem.setIfMissing(
                "summary",
                { pathItem -> pathItem.summary },
                { pathItem -> pathItem.summary = summary })
            pathItem.setIfMissing("get", { pathItem -> pathItem.get }, { pathItem -> pathItem.get = get })
            pathItem.setIfMissing("put", { pathItem -> pathItem.put }, { pathItem -> pathItem.put = put })
            pathItem.setIfMissing("post", { pathItem -> pathItem.post }, { pathItem -> pathItem.post = post })
            pathItem.setIfMissing("delete", { pathItem -> pathItem.delete }, { pathItem -> pathItem.delete = delete })
            pathItem.setIfMissing(
                "options",
                { pathItem -> pathItem.options },
                { pathItem -> pathItem.options = options })
            pathItem.setIfMissing("head", { pathItem -> pathItem.head }, { pathItem -> pathItem.head = head })
            pathItem.setIfMissing("patch", { pathItem -> pathItem.patch }, { pathItem -> pathItem.patch = patch })
            pathItem.setIfMissing("trace", { pathItem -> pathItem.trace }, { pathItem -> pathItem.trace = trace })
            pathItem.setIfMissing(
                "servers",
                { pathItem -> pathItem.servers },
                { pathItem -> pathItem.servers = servers })
            pathItem.setIfMissing(
                "parameters",
                { pathItem -> pathItem.parameters },
                { pathItem -> pathItem.parameters = parameters })
            pathItem.setIfMissing("ref", { pathItem -> pathItem.`$ref` }, { pathItem -> pathItem.`$ref` = `$ref` })
            pathItem.setIfMissing(
                "extensions",
                { pathItem -> pathItem.extensions },
                { pathItem -> pathItem.extensions = extensions })
        }
    }

    override fun get(): PathItem {
        return pathItem
    }

    private fun <T> PathItem.setIfMissing(
        propertyName: String,
        getProperty: (pathItem: PathItem) -> T?,
        setProperty: (pathItem: PathItem) -> Unit
    ) {
        val property = getProperty(this)
        if (property != null) log.warn(
            "{} in path item for path {} already exists. Hence skipping",
            propertyName,
            path
        ) else setProperty(this)
    }

}


class PathsMerger: MapMerger<PathItem>() {
    override fun whenKeyExists(key: String, value: PathItem) {
        val existingPathItem = map.getValue(key)
        PathItemMerger(key, existingPathItem).merge(value)
    }

    override fun get(): Paths {
        val path = Paths()
        map.forEach { (key, value) ->  path.addPathItem(key, value)}
        return path
    }
}


/**
 *  A class to merge a Map with string keys and value T
 */
open class MapMerger<T>: Mergeable<Map<String, T>> {
    private val log = LoggerFactory.getLogger(javaClass)
    protected val map = TreeMap<String, T>()

    override fun merge(from: Map<String, T>?) {
        from?.run {
            forEach { entry ->
                if (map.containsKey(entry.key)) {
                    whenKeyExists(entry.key, entry.value)
                } else {
                    map[entry.key] = entry.value
                }
            }
        }
    }

    open fun whenKeyExists(key: String, value: T) {
        log.warn("{} already exist in the map. Hence skipping", key)
    }

    override fun get(): Map<String, T>? {
        return if (map.size > 0) map else null
    }
}

/**
 *  A class to merge a list of components of type T
 */
class ListMerger<T>: Mergeable<List<T>> {
    private val log = LoggerFactory.getLogger(javaClass)
    private val list = ArrayList<T>()

    override fun merge(from: List<T>?) {
        from?.run {
            forEach { t ->
                if (list.contains(t)) {
                    log.warn("List already contains {}. Hence Skipping", t)
                } else {
                    list.add(t)
                }
            }
        }
    }

    override fun get(): List<T>? {
        return if (list.size > 0) list else null
    }
}


/**
 *  A class to merge open api Components object
 */
class ComponentsMerger: Mergeable<Components> {
    private val components = Components()

    private val schemasMerger = MapMerger<Schema<*>>()
    private val responsesMerger = MapMerger<ApiResponse>()
    private val parametersMerger = MapMerger<Parameter>()
    private val examplesMerger = MapMerger<Example>()
    private val requestBodiesMerger = MapMerger<RequestBody>()
    private val headersMerger = MapMerger<Header>()
    private val securitySchemasMerger = MapMerger<SecurityScheme>()
    private val linksMerger = MapMerger<Link>()
    private val callbacksMerger = MapMerger<Callback>()

    override fun merge(from: Components?) {
        from?.run {
            schemasMerger.merge(this.schemas)
            responsesMerger.merge(this.responses)
            parametersMerger.merge(this.parameters)
            examplesMerger.merge(this.examples)
            requestBodiesMerger.merge(this.requestBodies)
            headersMerger.merge(this.headers)
            securitySchemasMerger.merge(this.securitySchemes)
            linksMerger.merge(this.links)
            callbacksMerger.merge(this.callbacks)
        }
    }

    override fun get(): Components? {
        components.schemas = schemasMerger.get()
        components.responses = responsesMerger.get()
        components.parameters = parametersMerger.get()
        components.examples = examplesMerger.get()
        components.requestBodies = requestBodiesMerger.get()
        components.headers = headersMerger.get()
        components.securitySchemes = securitySchemasMerger.get()
        components.links = linksMerger.get()
        components.callbacks = callbacksMerger.get()
        return components
    }
}