// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

val azureSpringBootBomVersion = "3.11.0"

dependencies {
  api(projects.cosmotechApiCommonParent.cosmotechApiCommon)

  api(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))
  implementation(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))

  api("com.azure.spring:azure-spring-boot-starter-cosmos")
  implementation("com.azure.spring:azure-spring-boot-starter-storage")
  api("com.azure:azure-storage-blob-batch")
  implementation("com.azure.spring:azure-spring-boot-starter-active-directory")
  implementation("com.microsoft.azure.kusto:kusto-ingest:2.8.2") {
    exclude(group = "org.slf4j", module = "slf4j-api")
    because(
        "this depends on org.slf4j:slf4j-api 1.8.0-beta4 (pre 2.x)," +
            "which is not backward-compatible with 1.7.x." +
            "See http://www.slf4j.org/faq.html#changesInVersion200")
  }
}
