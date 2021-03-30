rootProject.name = "cosmotech-api"

include("common")

include("organization")

include("user")

include("connector")

project(":common").name = "cosmotech-api-common"

project(":organization").name = "cosmotech-organization-api"

project(":user").name = "cosmotech-user-api"

project(":connector").name = "cosmotech-connector-api"
