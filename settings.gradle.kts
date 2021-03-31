rootProject.name = "cosmotech-api-parent"

include("api")

include("common")

include("organization")

include("user")

include("connector")

project(":api").name = "cosmotech-api"

project(":common").name = "cosmotech-api-common"

project(":organization").name = "cosmotech-organization-api"

project(":user").name = "cosmotech-user-api"

project(":connector").name = "cosmotech-connector-api"
