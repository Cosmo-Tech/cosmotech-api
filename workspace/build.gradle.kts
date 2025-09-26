// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
  implementation(projects.cosmotechCommonApi)
  testImplementation(projects.cosmotechWorkspaceApi)
  testImplementation(projects.cosmotechDatasetApi)
}
