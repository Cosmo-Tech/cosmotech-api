// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

val azureSpringBootBomVersion = "3.10.2"

dependencies {
  api(projects.cosmotechApiCommonParent.cosmotechApiCommon)

  api(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))
  implementation(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))

  implementation(
      "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:2.5.6")
  implementation("org.springframework.security:spring-security-jwt:1.1.1.RELEASE")
  api("com.azure.spring:azure-spring-boot-starter-cosmos")
  implementation("com.azure.spring:azure-spring-boot-starter-storage")
  api("com.azure:azure-storage-blob-batch")
  implementation("com.azure.spring:azure-spring-boot-starter-active-directory")
  // com.azure.spring:azure-spring-boot-starter-active-directory provides this dependency
  // transitively,
  // but its version is incompatible at runtime with what is expected by
  // spring-security-oauth2-jose
  implementation("com.nimbusds:nimbus-jose-jwt:9.15.2")
}
