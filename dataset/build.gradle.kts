// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechOrganizationApi)
  testImplementation(projects.cosmotechSolutionApi)
  testImplementation(projects.cosmotechWorkspaceApi)
}
