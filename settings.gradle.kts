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
    "common:common",
    "common:azure",
    "common:events",
    "common:id",
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

project(":common").name = "cosmotech-api-common-parent"

project(":common:common").name = "cosmotech-api-common"

project(":common:azure").name = "cosmotech-api-common-azure"

project(":common:events").name = "cosmotech-api-common-events"

project(":common:id").name = "cosmotech-api-common-id"

project(":connector").name = "cosmotech-connector-api"

project(":dataset").name = "cosmotech-dataset-api"

project(":organization").name = "cosmotech-organization-api"

project(":scenario").name = "cosmotech-scenario-api"

project(":scenariorun").name = "cosmotech-scenariorun-api"

project(":solution").name = "cosmotech-solution-api"

project(":user").name = "cosmotech-user-api"

project(":workspace").name = "cosmotech-workspace-api"
