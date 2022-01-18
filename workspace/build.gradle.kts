// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

dependencies {
  api(projects.cosmotechApiCommonParent.cosmotechApiCommon)
  api(projects.cosmotechApiCommonParent.cosmotechApiCommonAzure)
  implementation(projects.cosmotechUserApi)
  implementation(projects.cosmotechOrganizationApi)
  implementation(projects.cosmotechSolutionApi)
}
