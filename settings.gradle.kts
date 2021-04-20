rootProject.name = "cosmotech-api-parent"

include("api")

include("common")

include("organization")

include("user")

include("connector")

include("dataset")

include("solution")

include("workspace")

include("scenario")

include("scenariorun")

include("platform")

project(":api").name = "cosmotech-api"

project(":common").name = "cosmotech-api-common"

project(":organization").name = "cosmotech-organization-api"

project(":user").name = "cosmotech-user-api"

project(":connector").name = "cosmotech-connector-api"

project(":dataset").name = "cosmotech-dataset-api"

project(":solution").name = "cosmotech-solution-api"

project(":workspace").name = "cosmotech-workspace-api"

project(":scenario").name = "cosmotech-scenario-api"

project(":scenariorun").name = "cosmotech-scenariorun-api"

project(":platform").name = "cosmotech-platform-api"
