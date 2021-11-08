// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

dependencies {
  api(projects.cosmotechApiCommonParent.cosmotechApiCommon)

  // TODO Extract those dependencies in a 'common/azure' sub-project,
  //  included dynamically if the 'platform' build property is 'azure'
  api("com.azure:azure-storage-blob-batch:12.11.1")
  implementation("com.azure:azure-core:1.21.0")
  implementation(
      "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:2.5.6")
  implementation("org.springframework.security:spring-security-jwt:1.1.1.RELEASE")
  api("com.azure.spring:azure-spring-boot-starter-cosmos")
  implementation("com.azure.spring:azure-spring-boot-starter-storage")
  implementation("com.azure.spring:azure-spring-boot-starter-active-directory")
  // com.azure.spring:azure-spring-boot-starter-active-directory provides this dependency
  // transitively,
  // but its version is incompatible at runtime with what is expected by
  // spring-security-oauth2-jose
  implementation("com.nimbusds:nimbus-jose-jwt:9.15.2")
}
