// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

// Gradle 7.0 feature previews
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

rootProject.name = "cosmotech-api-parent"

include(
    "api",
    "connector",
    "dataset",
    "organization",
    "scenario",
    "scenariorun",
    "solution",
    "user",
    "workspace",
)

project(":api").name = "cosmotech-api"

project(":connector").name = "cosmotech-connector-api"

project(":dataset").name = "cosmotech-dataset-api"

project(":organization").name = "cosmotech-organization-api"

project(":scenario").name = "cosmotech-scenario-api"

project(":scenariorun").name = "cosmotech-scenariorun-api"

project(":solution").name = "cosmotech-solution-api"

project(":user").name = "cosmotech-user-api"

project(":workspace").name = "cosmotech-workspace-api"
