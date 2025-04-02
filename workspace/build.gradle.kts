// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

plugins { id("org.jetbrains.kotlinx.kover") }
val testContainersLocalStackVersion = "1.20.6"
dependencies {
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  testImplementation("org.testcontainers:localstack:$testContainersLocalStackVersion")
  testImplementation(projects.cosmotechWorkspaceApi)
  testImplementation(projects.cosmotechDatasetApi)
  testImplementation(projects.cosmotechConnectorApi)
}
