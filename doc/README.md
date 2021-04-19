# Documentation for Cosmo Tech Plaform API

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *https://api.azure.cosmo-platform.com*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ConnectorApi* | [**findAllConnectors**](Apis/ConnectorApi.md#findallconnectors) | **GET** /connectors | List all Connectors
*ConnectorApi* | [**findConnectorById**](Apis/ConnectorApi.md#findconnectorbyid) | **GET** /connectors/{connector_id} | Get the details of an connector
*ConnectorApi* | [**registerConnector**](Apis/ConnectorApi.md#registerconnector) | **POST** /connectors | Register a new connector
*ConnectorApi* | [**unregisterConnector**](Apis/ConnectorApi.md#unregisterconnector) | **DELETE** /connectors/{connector_id} | Unregister an connector
*ConnectorApi* | [**uploadConnector**](Apis/ConnectorApi.md#uploadconnector) | **POST** /connectors/upload | Upload and register a new connector
*DatasetApi* | [**copyDataset**](Apis/DatasetApi.md#copydataset) | **POST** /organizations/{organization_id}/datasets/copy | Copy a Dataset to another Dataset. Source must have a read capable connector and Target a write capable connector.
*DatasetApi* | [**createDataset**](Apis/DatasetApi.md#createdataset) | **POST** /organizations/{organization_id}/datasets | Create a new dataset
*DatasetApi* | [**deleteDataset**](Apis/DatasetApi.md#deletedataset) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id} | Delete a dataset
*DatasetApi* | [**findAllDatasets**](Apis/DatasetApi.md#findalldatasets) | **GET** /organizations/{organization_id}/datasets | List all Datasets
*DatasetApi* | [**findDatasetById**](Apis/DatasetApi.md#finddatasetbyid) | **GET** /organizations/{organization_id}/datasets/{dataset_id} | Get the details of a dataset
*DatasetApi* | [**updateDataset**](Apis/DatasetApi.md#updatedataset) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id} | Update a dataset
*OrganizationApi* | [**findAllOrganizations**](Apis/OrganizationApi.md#findallorganizations) | **GET** /organizations | List all Organizations
*OrganizationApi* | [**findOrganizationById**](Apis/OrganizationApi.md#findorganizationbyid) | **GET** /organizations/{organization_id} | Get the details of an organization
*OrganizationApi* | [**registerOrganization**](Apis/OrganizationApi.md#registerorganization) | **POST** /organizations | Register a new organization
*OrganizationApi* | [**unregisterOrganization**](Apis/OrganizationApi.md#unregisterorganization) | **DELETE** /organizations/{organization_id} | Unregister an organization
*OrganizationApi* | [**updateOrganization**](Apis/OrganizationApi.md#updateorganization) | **PATCH** /organizations/{organization_id} | Update an organization
*PlatformApi* | [**createPlatform**](Apis/PlatformApi.md#createplatform) | **POST** /platform | Create a new platform
*PlatformApi* | [**getPlatform**](Apis/PlatformApi.md#getplatform) | **GET** /platform | Get the details of the platform
*PlatformApi* | [**updatePlatform**](Apis/PlatformApi.md#updateplatform) | **PATCH** /platform | Update a platform
*ScenarioApi* | [**compareScenarios**](Apis/ScenarioApi.md#comparescenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/compare/{compared_scenario_id} | Compare the Scenario with another one and returns the difference for parameters values
*ScenarioApi* | [**createScenario**](Apis/ScenarioApi.md#createscenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Create a new scenario
*ScenarioApi* | [**deleteScenario**](Apis/ScenarioApi.md#deletescenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Delete a scenario
*ScenarioApi* | [**findAllScenarios**](Apis/ScenarioApi.md#findallscenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | List all Scenarios
*ScenarioApi* | [**findScenarioById**](Apis/ScenarioApi.md#findscenariobyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Get the details of an scenario
*ScenarioApi* | [**getScenariosTree**](Apis/ScenarioApi.md#getscenariostree) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/tree | Get the Scenarios Tree
*ScenarioApi* | [**updateScenario**](Apis/ScenarioApi.md#updatescenario) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Update a scenario
*SimulationApi* | [**deleteSimulation**](Apis/SimulationApi.md#deletesimulation) | **DELETE** /organizations/{organization_id}/simulations/{simulation_id} | Delete a simulation
*SimulationApi* | [**findSimulationById**](Apis/SimulationApi.md#findsimulationbyid) | **GET** /organizations/{organization_id}/simulations/{simulation_id} | Get the details of a simulation
*SimulationApi* | [**getScenarioSimulation**](Apis/SimulationApi.md#getscenariosimulation) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/simulations/{simulation_id} | get the Simulation for the Scenario
*SimulationApi* | [**getScenarioSimulationLogs**](Apis/SimulationApi.md#getscenariosimulationlogs) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/simulations/{simulation_id}/logs | get the logs for the Simulation
*SimulationApi* | [**getScenarioSimulations**](Apis/SimulationApi.md#getscenariosimulations) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/simulations | get the list of Simulations for the Scenario
*SimulationApi* | [**getWorkspaceSimulations**](Apis/SimulationApi.md#getworkspacesimulations) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/simulations | get the list of Simulations for the Workspace
*SimulationApi* | [**runScenario**](Apis/SimulationApi.md#runscenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/run | run a Simulation for the Scenario
*SimulationApi* | [**searchSimulationLogs**](Apis/SimulationApi.md#searchsimulationlogs) | **POST** /organizations/{organization_id}/simulations/{simulation_id}/logs/search | Search the logs of a simulation
*SimulationApi* | [**searchSimulations**](Apis/SimulationApi.md#searchsimulations) | **POST** /organizations/{organization_id}/simulations/search | Search Simulations
*SimulationApi* | [**startSimulationContainers**](Apis/SimulationApi.md#startsimulationcontainers) | **POST** /organizations/{organization_id}/simulations/startcontainers | Start a new simulation with raw containers definition
*SimulationApi* | [**startSimulationScenario**](Apis/SimulationApi.md#startsimulationscenario) | **POST** /organizations/{organization_id}/simulations/start | Start a new simulation for a Scenario
*SimulationApi* | [**startSimulationSolution**](Apis/SimulationApi.md#startsimulationsolution) | **POST** /organizations/{organization_id}/simulations/startsolution | Start a new simulation for a Solution Run Template
*SolutionApi* | [**createSolution**](Apis/SolutionApi.md#createsolution) | **POST** /organizations/{organization_id}/solutions | Register a new solution
*SolutionApi* | [**deleteSolution**](Apis/SolutionApi.md#deletesolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution
*SolutionApi* | [**findAllSolutions**](Apis/SolutionApi.md#findallsolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions
*SolutionApi* | [**findSolutionById**](Apis/SolutionApi.md#findsolutionbyid) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution
*SolutionApi* | [**updateSolution**](Apis/SolutionApi.md#updatesolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution
*SolutionApi* | [**upload**](Apis/SolutionApi.md#upload) | **POST** /organizations/{organization_id}/solutions/upload | Upload and register a new solution
*UserApi* | [**authorizeUser**](Apis/UserApi.md#authorizeuser) | **GET** /oauth2/authorize | Authorize an User with OAuth2. Delegated to configured OAuth2 service
*UserApi* | [**findAllUsers**](Apis/UserApi.md#findallusers) | **GET** /users | List all Users
*UserApi* | [**findUserById**](Apis/UserApi.md#finduserbyid) | **GET** /users/{user_id} | Get the details of an user
*UserApi* | [**getCurrentUser**](Apis/UserApi.md#getcurrentuser) | **GET** /users/me | Get the details of an user
*UserApi* | [**getOrganizationCurrentUser**](Apis/UserApi.md#getorganizationcurrentuser) | **GET** /organizations/{organization_id}/me | Get the details of an user with roles for an Organization
*UserApi* | [**getWorkspaceCurrentUser**](Apis/UserApi.md#getworkspacecurrentuser) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/me | Get the details of an user with roles for a Workspace
*UserApi* | [**registerUser**](Apis/UserApi.md#registeruser) | **POST** /users | Register a new user
*UserApi* | [**unregisterUser**](Apis/UserApi.md#unregisteruser) | **DELETE** /users/{user_id} | Unregister an user
*UserApi* | [**updateUser**](Apis/UserApi.md#updateuser) | **PATCH** /users/{user_id} | Update an user
*ValidatorApi* | [**createValidator**](Apis/ValidatorApi.md#createvalidator) | **POST** /organizations/{organization_id}/datasets/validators | Register a new validator
*ValidatorApi* | [**createValidatorRun**](Apis/ValidatorApi.md#createvalidatorrun) | **POST** /organizations/{organization_id}/datasets/validators/{validator_id}/history | Register a new validator run
*ValidatorApi* | [**deleteValidator**](Apis/ValidatorApi.md#deletevalidator) | **DELETE** /organizations/{organization_id}/datasets/validators/{validator_id} | Delete a validator
*ValidatorApi* | [**deleteValidatorRun**](Apis/ValidatorApi.md#deletevalidatorrun) | **DELETE** /organizations/{organization_id}/datasets/validators/{validator_id}/history/{validatorrun_id} | Delete a validator run
*ValidatorApi* | [**findAllValidatorRuns**](Apis/ValidatorApi.md#findallvalidatorruns) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id}/history | List all Validator Runs
*ValidatorApi* | [**findAllValidators**](Apis/ValidatorApi.md#findallvalidators) | **GET** /organizations/{organization_id}/datasets/validators | List all Validators
*ValidatorApi* | [**findValidatorById**](Apis/ValidatorApi.md#findvalidatorbyid) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id} | Get the details of a validator
*ValidatorApi* | [**findValidatorRunById**](Apis/ValidatorApi.md#findvalidatorrunbyid) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id}/history/{validatorrun_id} | Get the details of a validator run
*ValidatorApi* | [**runValidator**](Apis/ValidatorApi.md#runvalidator) | **POST** /organizations/{organization_id}/datasets/validators/{validator_id}/run | Run a Validator
*WorkspaceApi* | [**createWorkspace**](Apis/WorkspaceApi.md#createworkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace
*WorkspaceApi* | [**deleteWorkspace**](Apis/WorkspaceApi.md#deleteworkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace
*WorkspaceApi* | [**findAllWorkspaces**](Apis/WorkspaceApi.md#findallworkspaces) | **GET** /organizations/{organization_id}/workspaces | List all Workspaces
*WorkspaceApi* | [**findWorkspaceById**](Apis/WorkspaceApi.md#findworkspacebyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of an workspace
*WorkspaceApi* | [**updateWorkspace**](Apis/WorkspaceApi.md#updateworkspace) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id} | Update a workspace


<a name="documentation-for-models"></a>
## Documentation for Models

 - [Connector](./Models/Connector.md)
 - [ConnectorParameter](./Models/ConnectorParameter.md)
 - [ConnectorParameterGroup](./Models/ConnectorParameterGroup.md)
 - [Dataset](./Models/Dataset.md)
 - [DatasetCompatibility](./Models/DatasetCompatibility.md)
 - [DatasetConnector](./Models/DatasetConnector.md)
 - [DatasetCopyParameters](./Models/DatasetCopyParameters.md)
 - [Organization](./Models/Organization.md)
 - [OrganizationService](./Models/OrganizationService.md)
 - [OrganizationServices](./Models/OrganizationServices.md)
 - [OrganizationUser](./Models/OrganizationUser.md)
 - [Platform](./Models/Platform.md)
 - [PlatformService](./Models/PlatformService.md)
 - [PlatformServices](./Models/PlatformServices.md)
 - [RunTemplate](./Models/RunTemplate.md)
 - [RunTemplateParameter](./Models/RunTemplateParameter.md)
 - [RunTemplateParameterGroup](./Models/RunTemplateParameterGroup.md)
 - [RunTemplateParameterValue](./Models/RunTemplateParameterValue.md)
 - [RunTemplateResourceStorage](./Models/RunTemplateResourceStorage.md)
 - [Scenario](./Models/Scenario.md)
 - [ScenarioAllOf](./Models/ScenarioAllOf.md)
 - [ScenarioBase](./Models/ScenarioBase.md)
 - [ScenarioChangedParameterValue](./Models/ScenarioChangedParameterValue.md)
 - [ScenarioComparisonResult](./Models/ScenarioComparisonResult.md)
 - [ScenarioRunTemplateParameterValue](./Models/ScenarioRunTemplateParameterValue.md)
 - [ScenarioUser](./Models/ScenarioUser.md)
 - [Simulation](./Models/Simulation.md)
 - [SimulationAllOf](./Models/SimulationAllOf.md)
 - [SimulationBase](./Models/SimulationBase.md)
 - [SimulationContainerLog](./Models/SimulationContainerLog.md)
 - [SimulationContainerLogs](./Models/SimulationContainerLogs.md)
 - [SimulationContainers](./Models/SimulationContainers.md)
 - [SimulationLogs](./Models/SimulationLogs.md)
 - [SimulationLogsOptions](./Models/SimulationLogsOptions.md)
 - [SimulationSearch](./Models/SimulationSearch.md)
 - [SimulationStartContainers](./Models/SimulationStartContainers.md)
 - [SimulationStartScenario](./Models/SimulationStartScenario.md)
 - [SimulationStartSolution](./Models/SimulationStartSolution.md)
 - [Solution](./Models/Solution.md)
 - [User](./Models/User.md)
 - [UserDetails](./Models/UserDetails.md)
 - [UserDetailsAllOf](./Models/UserDetailsAllOf.md)
 - [UserOrganization](./Models/UserOrganization.md)
 - [UserWorkspace](./Models/UserWorkspace.md)
 - [Validator](./Models/Validator.md)
 - [ValidatorRun](./Models/ValidatorRun.md)
 - [Workspace](./Models/Workspace.md)
 - [WorkspaceService](./Models/WorkspaceService.md)
 - [WorkspaceServices](./Models/WorkspaceServices.md)
 - [WorkspaceSolution](./Models/WorkspaceSolution.md)
 - [WorkspaceUser](./Models/WorkspaceUser.md)
 - [WorkspaceWebApp](./Models/WorkspaceWebApp.md)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

<a name="oAuth2AuthCode"></a>
### oAuth2AuthCode

- **Type**: OAuth
- **Flow**: implicit
- **Authorization URL**: oauth2/authorize
- **Scopes**: N/A

