import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
   id("org.openapi.generator")
}

sourceSets {
   main {
      java.srcDirs("$buildDir/generated-sources/openapi/src/main/kotlin")
   }
//   test {
   //Excluded because the OpenAPI Generator generates test classes that expect the Service Implementation to be
   //present, which is not the case when serviceInterface is true
//      java.srcDirs("$buildDir/generated-sources/openapi/src/test/kotlin")
//   }
}

tasks.getByName<ValidateTask>("openApiValidate") {
   input = "${projectDir}/src/main/openapi/organizations.yaml"
}

tasks.getByName<GenerateTask>("openApiGenerate") {
   input = "${projectDir}/src/main/openapi/organizations.yaml"
   outputDir.set("$buildDir/generated-sources/openapi")
   generatorName.set("kotlin-spring")
   apiPackage.set("com.cosmotech.organization.api")
   modelPackage.set("com.cosmotech.organization.domain")
//   globalProperties.set(mapOf(
//           "apis" to "",
//           "models" to ""
//   ))
   additionalProperties.set(mapOf(
           "title" to "Cosmo Tech Organization Manager API",
           "basePackage" to "com.cosmotech.organization",
           "configPackage" to "com.cosmotech.organization.config",
           "enumPropertyNaming" to "original",
           "serviceInterface" to true,
           "swaggerAnnotations" to true
   ))
}