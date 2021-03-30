import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

plugins { id("org.openapi.generator") }

dependencies { api(project(":cosmotech-api-common")) }

sourceSets {
  main { java.srcDirs("$buildDir/generated-sources/openapi/src/main/kotlin") }
  test { java.srcDirs("$buildDir/generated-sources/openapi/src/test/kotlin") }
}

tasks.getByName<ValidateTask>("openApiValidate") {
  input = "${projectDir}/src/main/openapi/connectors.yaml"
}

tasks.getByName<GenerateTask>("openApiGenerate") {
  input = "${projectDir}/src/main/openapi/connectors.yaml"
  outputDir.set("$buildDir/generated-sources/openapi")
  generatorName.set("kotlin-spring")
  apiPackage.set("com.cosmotech.connector.api")
  modelPackage.set("com.cosmotech.connector.domain")
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
          "title" to "Cosmo Tech Organization Manager API",
          "basePackage" to "com.cosmotech.connector",
          "configPackage" to "com.cosmotech.connector.config",
          "enumPropertyNaming" to "original",
          "serviceInterface" to true,
          "swaggerAnnotations" to true))
}
