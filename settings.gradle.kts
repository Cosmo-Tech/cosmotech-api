rootProject.name = "cosmotech-api"

include("common")

include("organization")

include("user")

project(":common").name = "cosmotech-api-common"

project(":organization").name = "cosmotech-organization-api"

project(":user").name = "cosmotech-user-api"
