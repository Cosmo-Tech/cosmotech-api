import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

repositories {
    mavenLocal()
    maven {
          name = "GitHubPackages"
          url = uri("https://maven.pkg.github.com/argoproj-labs/argo-client-java")
          credentials {
              username = project.findProperty("gpr.user")?.toString() ?: System.getenv("GPR_API_COMMON_USER")
              password = project.findProperty("gpr.key")?.toString() ?: System.getenv("GPR_API_COMMON_KEY")
          }
    }
    mavenCentral()
}

dependencies {
  implementation("io.argoproj.workflow:argo-client-java:v3.0.1")
  api(project(":cosmotech-api-common"))
}

sourceSets {
  main { java.srcDirs("$buildDir/generated-sources/openapi/src/main/kotlin") }
  test { java.srcDirs("$buildDir/generated-sources/openapi/src/test/kotlin") }
}

tasks.getByName<ValidateTask>("openApiValidate") {
  inputSpec.set("${projectDir}/src/main/openapi/scenarioruns.yaml")
}

tasks.getByName<GenerateTask>("openApiGenerate") {
  inputSpec.set("${projectDir}/src/main/openapi/scenarioruns.yaml")
  outputDir.set("$buildDir/generated-sources/openapi")
  generatorName.set("kotlin-spring")
  apiPackage.set("com.cosmotech.scenariorun.api")
  modelPackage.set("com.cosmotech.scenariorun.domain")
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
          "title" to "Cosmo Tech ScenarioRun Manager API",
          "basePackage" to "com.cosmotech",
          "configPackage" to "com.cosmotech.scenariorun.config",
          "enumPropertyNaming" to "original",
          "exceptionHandler" to false,
          "serviceInterface" to true,
          "swaggerAnnotations" to false,
          "useTags" to true))
}
