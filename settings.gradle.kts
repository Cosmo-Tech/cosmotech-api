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
    "workspace",
    "twingraph",
    "metrics",
    "scenariorunresult",
    "runner",
    "run")

project(":api").name = "cosmotech-api"

project(":connector").name = "cosmotech-connector-api"

project(":dataset").name = "cosmotech-dataset-api"

project(":organization").name = "cosmotech-organization-api"

project(":scenario").name = "cosmotech-scenario-api"

project(":scenariorun").name = "cosmotech-scenariorun-api"

project(":solution").name = "cosmotech-solution-api"

project(":workspace").name = "cosmotech-workspace-api"

project(":twingraph").name = "cosmotech-twingraph-api"

project(":metrics").name = "cosmotech-metrics-service"

project(":scenariorunresult").name = "cosmotech-scenariorunresult-api"

project(":runner").name = "cosmotech-runner-api"

project(":run").name = "cosmotech-run-api"

