// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  testImplementation(projects.cosmotechWorkspaceApi)
  testImplementation(projects.cosmotechDatasetApi)
  testImplementation(projects.cosmotechConnectorApi)
}
