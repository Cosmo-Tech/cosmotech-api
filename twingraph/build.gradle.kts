// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
  implementation(projects.cosmotechOrganizationApi)
  implementation("com.redislabs:jredisgraph:2.5.1")
  implementation("org.json:json:20220924")
}
