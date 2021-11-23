// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

val azureSpringBootBomVersion = "3.10.2"

dependencies {
  api(projects.cosmotechApiCommonParent.cosmotechApiCommon)

  api(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))
  implementation(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))

  api("com.azure.spring:azure-spring-boot-starter-cosmos")
  implementation("com.azure.spring:azure-spring-boot-starter-storage")
  api("com.azure:azure-storage-blob-batch")
  implementation("com.azure.spring:azure-spring-boot-starter-active-directory")
}
