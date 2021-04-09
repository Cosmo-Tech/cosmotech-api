import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

dependencies { api(project(":cosmotech-api-common")) }

sourceSets {
  main { java.srcDirs("$buildDir/generated-sources/openapi/src/main/kotlin") }
  test { java.srcDirs("$buildDir/generated-sources/openapi/src/test/kotlin") }
}

tasks.getByName<ValidateTask>("openApiValidate") {
  inputSpec.set("${projectDir}/src/main/openapi/users.yaml")
}

tasks.getByName<GenerateTask>("openApiGenerate") {
  inputSpec.set("${projectDir}/src/main/openapi/users.yaml")
  outputDir.set("$buildDir/generated-sources/openapi")
  generatorName.set("kotlin-spring")
  apiPackage.set("com.cosmotech.user.api")
  modelPackage.set("com.cosmotech.user.domain")
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
          "title" to "Cosmo Tech User Manager API",
          "basePackage" to "com.cosmotech",
          "configPackage" to "com.cosmotech.user.config",
          "enumPropertyNaming" to "original",
          "exceptionHandler" to false,
          "serviceInterface" to true,
          "swaggerAnnotations" to false,
          "useTags" to true))
}
