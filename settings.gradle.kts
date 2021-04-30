rootProject.name = "cosmotech-api-parent"

include(
    "api",
    "common",
    "connector",
    "dataset",
    "organization",
    "scenario",
    "scenariorun",
    "solution",
    "user",
    // TODO Upgrading to Gradle 7.0 will allow to use Kotlin 1.4 language features, such as trailing
    // commas, which can be useful here
    "workspace")

project(":api").name = "cosmotech-api"

project(":common").name = "cosmotech-api-common"

project(":connector").name = "cosmotech-connector-api"

project(":dataset").name = "cosmotech-dataset-api"

project(":organization").name = "cosmotech-organization-api"

project(":scenario").name = "cosmotech-scenario-api"

project(":scenariorun").name = "cosmotech-scenariorun-api"

project(":solution").name = "cosmotech-solution-api"

project(":user").name = "cosmotech-user-api"

project(":workspace").name = "cosmotech-workspace-api"
