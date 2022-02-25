// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

val azureSpringBootBomVersion = "3.13.0"
val azureSDKBomVersion = "1.1.0"
val azureKustoIngestVersion = "3.0.0"

dependencies {
  api(projects.cosmotechApiCommonParent.cosmotechApiCommon)

  api(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))
  implementation(platform("com.azure.spring:azure-spring-boot-bom:$azureSpringBootBomVersion"))
  api(platform("com.azure:azure-sdk-bom:$azureSDKBomVersion"))

  api("com.azure.spring:azure-spring-boot-starter-cosmos")
  implementation("com.azure.spring:azure-spring-boot-starter-storage")
  api("com.azure:azure-storage-blob-batch")
  implementation("com.azure.spring:azure-spring-boot-starter-active-directory")
  implementation("com.microsoft.azure.kusto:kusto-ingest:$azureKustoIngestVersion") {
    exclude(group = "org.slf4j", module = "slf4j-api")
    because(
        "this depends on org.slf4j:slf4j-api 1.8.0-beta4 (pre 2.x)," +
            "which is not backward-compatible with 1.7.x." +
            "See http://www.slf4j.org/faq.html#changesInVersion200")
  }
  implementation("com.azure:azure-messaging-eventhubs")
  implementation("com.azure:azure-identity")
}
