# Documentation for Cosmo Tech Plaform API

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *https://dev.api.cosmotech.com*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ConnectorApi* | [**findAllConnectors**](Apis/ConnectorApi.md#findallconnectors) | **GET** /connectors | List all Connectors
*ConnectorApi* | [**findConnectorById**](Apis/ConnectorApi.md#findconnectorbyid) | **GET** /connectors/{connector_id} | Get the details of a connector
*ConnectorApi* | [**registerConnector**](Apis/ConnectorApi.md#registerconnector) | **POST** /connectors | Register a new connector
*ConnectorApi* | [**unregisterConnector**](Apis/ConnectorApi.md#unregisterconnector) | **DELETE** /connectors/{connector_id} | Unregister a connector
*DatasetApi* | [**addOrReplaceDatasetCompatibilityElements**](Apis/DatasetApi.md#addorreplacedatasetcompatibilityelements) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Add Dataset Compatibility elements.
*DatasetApi* | [**copyDataset**](Apis/DatasetApi.md#copydataset) | **POST** /organizations/{organization_id}/datasets/copy | Copy a Dataset to another Dataset. Source must have a read capable connector and Target a write capable connector.
*DatasetApi* | [**createDataset**](Apis/DatasetApi.md#createdataset) | **POST** /organizations/{organization_id}/datasets | Create a new Dataset
*DatasetApi* | [**deleteDataset**](Apis/DatasetApi.md#deletedataset) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id} | Delete a dataset
*DatasetApi* | [**findAllDatasets**](Apis/DatasetApi.md#findalldatasets) | **GET** /organizations/{organization_id}/datasets | List all Datasets
*DatasetApi* | [**findDatasetById**](Apis/DatasetApi.md#finddatasetbyid) | **GET** /organizations/{organization_id}/datasets/{dataset_id} | Get the details of a Dataset
*DatasetApi* | [**removeAllDatasetCompatibilityElements**](Apis/DatasetApi.md#removealldatasetcompatibilityelements) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Remove all Dataset Compatibility elements from the Dataset specified
*DatasetApi* | [**searchDatasets**](Apis/DatasetApi.md#searchdatasets) | **POST** /organizations/{organization_id}/datasets/search | Search Datasets
*DatasetApi* | [**updateDataset**](Apis/DatasetApi.md#updatedataset) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id} | Update a dataset
*OrganizationApi* | [**addOrReplaceUsersInOrganization**](Apis/OrganizationApi.md#addorreplaceusersinorganization) | **POST** /organizations/{organization_id}/users | Add (or replace) users in the Organization specified
*OrganizationApi* | [**findAllOrganizations**](Apis/OrganizationApi.md#findallorganizations) | **GET** /organizations | List all Organizations
*OrganizationApi* | [**findOrganizationById**](Apis/OrganizationApi.md#findorganizationbyid) | **GET** /organizations/{organization_id} | Get the details of an Organization
*OrganizationApi* | [**registerOrganization**](Apis/OrganizationApi.md#registerorganization) | **POST** /organizations | Register a new organization
*OrganizationApi* | [**removeAllUsersInOrganization**](Apis/OrganizationApi.md#removeallusersinorganization) | **DELETE** /organizations/{organization_id}/users | Remove all users from the Organization specified
*OrganizationApi* | [**removeUserFromOrganization**](Apis/OrganizationApi.md#removeuserfromorganization) | **DELETE** /organizations/{organization_id}/users/{user_id} | Remove the specified user from the given Organization
*OrganizationApi* | [**unregisterOrganization**](Apis/OrganizationApi.md#unregisterorganization) | **DELETE** /organizations/{organization_id} | Unregister an organization
*OrganizationApi* | [**updateOrganization**](Apis/OrganizationApi.md#updateorganization) | **PATCH** /organizations/{organization_id} | Update an Organization
*OrganizationApi* | [**updateSolutionsContainerRegistryByOrganizationId**](Apis/OrganizationApi.md#updatesolutionscontainerregistrybyorganizationid) | **PATCH** /organizations/{organization_id}/services/solutionsContainerRegistry | Update the solutions container registry configuration for the Organization specified
*OrganizationApi* | [**updateStorageByOrganizationId**](Apis/OrganizationApi.md#updatestoragebyorganizationid) | **PATCH** /organizations/{organization_id}/services/storage | Update storage configuration for the Organization specified
*OrganizationApi* | [**updateTenantCredentialsByOrganizationId**](Apis/OrganizationApi.md#updatetenantcredentialsbyorganizationid) | **PATCH** /organizations/{organization_id}/services/tenantCredentials | Update tenant credentials for the Organization specified
*ScenarioApi* | [**addOrReplaceScenarioParameterValues**](Apis/ScenarioApi.md#addorreplacescenarioparametervalues) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/parameterValues | Add (or replace) Parameter Values for the Scenario specified
*ScenarioApi* | [**addOrReplaceUsersInScenario**](Apis/ScenarioApi.md#addorreplaceusersinscenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/users | Add (or replace) users in the Scenario specified
*ScenarioApi* | [**compareScenarios**](Apis/ScenarioApi.md#comparescenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/compare/{compared_scenario_id} | Compare the Scenario with another one and returns the difference for parameters values
*ScenarioApi* | [**createScenario**](Apis/ScenarioApi.md#createscenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Create a new Scenario
*ScenarioApi* | [**deleteAllScenarios**](Apis/ScenarioApi.md#deleteallscenarios) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Delete all Scenarios of the Workspace
*ScenarioApi* | [**deleteScenario**](Apis/ScenarioApi.md#deletescenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Delete a scenario
*ScenarioApi* | [**downloadScenarioData**](Apis/ScenarioApi.md#downloadscenariodata) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/downloads | Download Scenario data
*ScenarioApi* | [**findAllScenarios**](Apis/ScenarioApi.md#findallscenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | List all Scenarios
*ScenarioApi* | [**findAllScenariosByValidationStatus**](Apis/ScenarioApi.md#findallscenariosbyvalidationstatus) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/{validationStatus} | List all Scenarios by validation status
*ScenarioApi* | [**findScenarioById**](Apis/ScenarioApi.md#findscenariobyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Get the details of an scenario
*ScenarioApi* | [**getScenarioDataDownloadJobInfo**](Apis/ScenarioApi.md#getscenariodatadownloadjobinfo) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/downloads/{download_id} | Get Scenario data download URL
*ScenarioApi* | [**getScenarioValidationStatusById**](Apis/ScenarioApi.md#getscenariovalidationstatusbyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/ValidationStatus | Get the validation status of an scenario
*ScenarioApi* | [**getScenariosTree**](Apis/ScenarioApi.md#getscenariostree) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/tree | Get the Scenarios Tree
*ScenarioApi* | [**removeAllScenarioParameterValues**](Apis/ScenarioApi.md#removeallscenarioparametervalues) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/parameterValues | Remove all Parameter Values from the Scenario specified
*ScenarioApi* | [**removeAllUsersOfScenario**](Apis/ScenarioApi.md#removeallusersofscenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/users | Remove all users from the Scenario specified
*ScenarioApi* | [**removeUserFromScenario**](Apis/ScenarioApi.md#removeuserfromscenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/users/{user_id} | Remove the specified user from the given Scenario
*ScenarioApi* | [**updateScenario**](Apis/ScenarioApi.md#updatescenario) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Update a scenario
*ScenariorunApi* | [**deleteHistoricalDataOrganization**](Apis/ScenariorunApi.md#deletehistoricaldataorganization) | **DELETE** /organizations/{organization_id}/scenarioruns/deletehistoricaldata | Delete all historical ScenarioRuns in the database
*ScenariorunApi* | [**deleteHistoricalScenarioRunsByScenario**](Apis/ScenariorunApi.md#deletehistoricalscenariorunsbyscenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns | Delete all historical ScenarioRuns for the Scenario
*ScenariorunApi* | [**deleteScenarioRun**](Apis/ScenariorunApi.md#deletescenariorun) | **DELETE** /organizations/{organization_id}/scenarioruns/{scenariorun_id} | Delete a scenariorun
*ScenariorunApi* | [**findScenarioRunById**](Apis/ScenariorunApi.md#findscenariorunbyid) | **GET** /organizations/{organization_id}/scenarioruns/{scenariorun_id} | Get the details of a scenariorun
*ScenariorunApi* | [**getScenarioRunCumulatedLogs**](Apis/ScenariorunApi.md#getscenarioruncumulatedlogs) | **GET** /organizations/{organization_id}/scenarioruns/{scenariorun_id}/cumulatedlogs | Get the cumulated logs of a scenariorun
*ScenariorunApi* | [**getScenarioRunLogs**](Apis/ScenariorunApi.md#getscenariorunlogs) | **GET** /organizations/{organization_id}/scenarioruns/{scenariorun_id}/logs | get the logs for the ScenarioRun
*ScenariorunApi* | [**getScenarioRunStatus**](Apis/ScenariorunApi.md#getscenariorunstatus) | **GET** /organizations/{organization_id}/scenarioruns/{scenariorun_id}/status | get the status for the ScenarioRun
*ScenariorunApi* | [**getScenarioRuns**](Apis/ScenariorunApi.md#getscenarioruns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns | get the list of ScenarioRuns for the Scenario
*ScenariorunApi* | [**getWorkspaceScenarioRuns**](Apis/ScenariorunApi.md#getworkspacescenarioruns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarioruns | get the list of ScenarioRuns for the Workspace
*ScenariorunApi* | [**runScenario**](Apis/ScenariorunApi.md#runscenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/run | run a ScenarioRun for the Scenario
*ScenariorunApi* | [**searchScenarioRuns**](Apis/ScenariorunApi.md#searchscenarioruns) | **POST** /organizations/{organization_id}/scenarioruns/search | Search ScenarioRuns
*ScenariorunApi* | [**startScenarioRunContainers**](Apis/ScenariorunApi.md#startscenarioruncontainers) | **POST** /organizations/{organization_id}/scenarioruns/startcontainers | Start a new scenariorun with raw containers definition
*ScenariorunApi* | [**stopScenarioRun**](Apis/ScenariorunApi.md#stopscenariorun) | **POST** /organizations/{organization_id}/scenarioruns/{scenariorun_id}/stop | stop a ScenarioRun for the Scenario
*SolutionApi* | [**addOrReplaceParameterGroups**](Apis/SolutionApi.md#addorreplaceparametergroups) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Add Parameter Groups. Any item with the same ID will be overwritten
*SolutionApi* | [**addOrReplaceParameters**](Apis/SolutionApi.md#addorreplaceparameters) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameters | Add Parameters. Any item with the same ID will be overwritten
*SolutionApi* | [**addOrReplaceRunTemplates**](Apis/SolutionApi.md#addorreplaceruntemplates) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Add Run Templates. Any item with the same ID will be overwritten
*SolutionApi* | [**createSolution**](Apis/SolutionApi.md#createsolution) | **POST** /organizations/{organization_id}/solutions | Register a new solution
*SolutionApi* | [**deleteSolution**](Apis/SolutionApi.md#deletesolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution
*SolutionApi* | [**deleteSolutionRunTemplate**](Apis/SolutionApi.md#deletesolutionruntemplate) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Remove the specified Solution Run Template
*SolutionApi* | [**downloadRunTemplateHandler**](Apis/SolutionApi.md#downloadruntemplatehandler) | **GET** /organizations/{organization_id}/solutions/{solution_id}/runtemplates/{run_template_id}/handlers/{handler_id}/download | Download a Run Template step handler zip file
*SolutionApi* | [**findAllSolutions**](Apis/SolutionApi.md#findallsolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions
*SolutionApi* | [**findSolutionById**](Apis/SolutionApi.md#findsolutionbyid) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution
*SolutionApi* | [**removeAllRunTemplates**](Apis/SolutionApi.md#removeallruntemplates) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Remove all Run Templates from the Solution specified
*SolutionApi* | [**removeAllSolutionParameterGroups**](Apis/SolutionApi.md#removeallsolutionparametergroups) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Remove all Parameter Groups from the Solution specified
*SolutionApi* | [**removeAllSolutionParameters**](Apis/SolutionApi.md#removeallsolutionparameters) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameters | Remove all Parameters from the Solution specified
*SolutionApi* | [**updateSolution**](Apis/SolutionApi.md#updatesolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution
*SolutionApi* | [**updateSolutionRunTemplate**](Apis/SolutionApi.md#updatesolutionruntemplate) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Update the specified Solution Run Template
*SolutionApi* | [**uploadRunTemplateHandler**](Apis/SolutionApi.md#uploadruntemplatehandler) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runtemplates/{run_template_id}/handlers/{handler_id}/upload | Upload a Run Template step handler zip file
*UserApi* | [**authorizeUser**](Apis/UserApi.md#authorizeuser) | **GET** /oauth2/authorize | Authorize an User with OAuth2. Delegated to configured OAuth2 service
*UserApi* | [**findAllUsers**](Apis/UserApi.md#findallusers) | **GET** /users | List all Users
*UserApi* | [**findUserById**](Apis/UserApi.md#finduserbyid) | **GET** /users/{user_id} | Get the details of an user
*UserApi* | [**getCurrentUser**](Apis/UserApi.md#getcurrentuser) | **GET** /users/me | Get the details of the logged-in User
*UserApi* | [**getOrganizationCurrentUser**](Apis/UserApi.md#getorganizationcurrentuser) | **GET** /organizations/{organization_id}/me | Get the details of a logged-in User with roles for an Organization
*UserApi* | [**getWorkspaceCurrentUser**](Apis/UserApi.md#getworkspacecurrentuser) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/me | Get the details of the logged-in user with roles for a Workspace
*UserApi* | [**registerUser**](Apis/UserApi.md#registeruser) | **POST** /users | Register a new user
*UserApi* | [**testPlatform**](Apis/UserApi.md#testplatform) | **GET** /test | test platform API call
*UserApi* | [**unregisterUser**](Apis/UserApi.md#unregisteruser) | **DELETE** /users/{user_id} | Unregister an user
*UserApi* | [**updateUser**](Apis/UserApi.md#updateuser) | **PATCH** /users/{user_id} | Update a User
*ValidatorApi* | [**createValidator**](Apis/ValidatorApi.md#createvalidator) | **POST** /organizations/{organization_id}/datasets/validators | Register a new validator
*ValidatorApi* | [**createValidatorRun**](Apis/ValidatorApi.md#createvalidatorrun) | **POST** /organizations/{organization_id}/datasets/validators/{validator_id}/history | Register a new validator run
*ValidatorApi* | [**deleteValidator**](Apis/ValidatorApi.md#deletevalidator) | **DELETE** /organizations/{organization_id}/datasets/validators/{validator_id} | Delete a validator
*ValidatorApi* | [**deleteValidatorRun**](Apis/ValidatorApi.md#deletevalidatorrun) | **DELETE** /organizations/{organization_id}/datasets/validators/{validator_id}/history/{validatorrun_id} | Delete a validator run
*ValidatorApi* | [**findAllValidatorRuns**](Apis/ValidatorApi.md#findallvalidatorruns) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id}/history | List all Validator Runs
*ValidatorApi* | [**findAllValidators**](Apis/ValidatorApi.md#findallvalidators) | **GET** /organizations/{organization_id}/datasets/validators | List all Validators
*ValidatorApi* | [**findValidatorById**](Apis/ValidatorApi.md#findvalidatorbyid) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id} | Get the details of a validator
*ValidatorApi* | [**findValidatorRunById**](Apis/ValidatorApi.md#findvalidatorrunbyid) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id}/history/{validatorrun_id} | Get the details of a validator run
*ValidatorApi* | [**runValidator**](Apis/ValidatorApi.md#runvalidator) | **POST** /organizations/{organization_id}/datasets/validators/{validator_id}/run | Run a Validator
*WorkspaceApi* | [**addWorkspaceAccess**](Apis/WorkspaceApi.md#addworkspaceaccess) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/access | add a control acccess to the Workspace
*WorkspaceApi* | [**createWorkspace**](Apis/WorkspaceApi.md#createworkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace
*WorkspaceApi* | [**deleteAllWorkspaceFiles**](Apis/WorkspaceApi.md#deleteallworkspacefiles) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files | Delete all Workspace files
*WorkspaceApi* | [**deleteWorkspace**](Apis/WorkspaceApi.md#deleteworkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace
*WorkspaceApi* | [**deleteWorkspaceFile**](Apis/WorkspaceApi.md#deleteworkspacefile) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files/delete | Delete a workspace file
*WorkspaceApi* | [**downloadWorkspaceFile**](Apis/WorkspaceApi.md#downloadworkspacefile) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files/download | Download the Workspace File specified
*WorkspaceApi* | [**findAllWorkspaceFiles**](Apis/WorkspaceApi.md#findallworkspacefiles) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files | List all Workspace files
*WorkspaceApi* | [**findAllWorkspaces**](Apis/WorkspaceApi.md#findallworkspaces) | **GET** /organizations/{organization_id}/workspaces | List all Workspaces
*WorkspaceApi* | [**findWorkspaceById**](Apis/WorkspaceApi.md#findworkspacebyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of an workspace
*WorkspaceApi* | [**getWorkspaceAccess**](Apis/WorkspaceApi.md#getworkspaceaccess) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | get a control acccess for the Workspace
*WorkspaceApi* | [**getWorkspaceSecurity**](Apis/WorkspaceApi.md#getworkspacesecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security | Get the Workspace security information
*WorkspaceApi* | [**removeWorkspaceAccess**](Apis/WorkspaceApi.md#removeworkspaceaccess) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Remove the specified access from the given Organization Workspace
*WorkspaceApi* | [**setWorkspaceDefaultSecurity**](Apis/WorkspaceApi.md#setworkspacedefaultsecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/default | set the Workspace default security
*WorkspaceApi* | [**updateWorkspace**](Apis/WorkspaceApi.md#updateworkspace) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id} | Update a workspace
*WorkspaceApi* | [**uploadWorkspaceFile**](Apis/WorkspaceApi.md#uploadworkspacefile) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/files | Upload a file for the Workspace


<a name="documentation-for-models"></a>
## Documentation for Models

 - [Connector](./Models/Connector.md)
 - [ConnectorParameter](./Models/ConnectorParameter.md)
 - [ConnectorParameterGroup](./Models/ConnectorParameterGroup.md)
 - [Dataset](./Models/Dataset.md)
 - [DatasetCompatibility](./Models/DatasetCompatibility.md)
 - [DatasetConnector](./Models/DatasetConnector.md)
 - [DatasetCopyParameters](./Models/DatasetCopyParameters.md)
 - [DatasetSearch](./Models/DatasetSearch.md)
 - [Organization](./Models/Organization.md)
 - [OrganizationService](./Models/OrganizationService.md)
 - [OrganizationServices](./Models/OrganizationServices.md)
 - [OrganizationUser](./Models/OrganizationUser.md)
 - [RunTemplate](./Models/RunTemplate.md)
 - [RunTemplateHandlerId](./Models/RunTemplateHandlerId.md)
 - [RunTemplateParameter](./Models/RunTemplateParameter.md)
 - [RunTemplateParameterGroup](./Models/RunTemplateParameterGroup.md)
 - [RunTemplateParameterValue](./Models/RunTemplateParameterValue.md)
 - [RunTemplateStepSource](./Models/RunTemplateStepSource.md)
 - [Scenario](./Models/Scenario.md)
 - [ScenarioChangedParameterValue](./Models/ScenarioChangedParameterValue.md)
 - [ScenarioComparisonResult](./Models/ScenarioComparisonResult.md)
 - [ScenarioDataDownloadInfo](./Models/ScenarioDataDownloadInfo.md)
 - [ScenarioDataDownloadJob](./Models/ScenarioDataDownloadJob.md)
 - [ScenarioJobState](./Models/ScenarioJobState.md)
 - [ScenarioLastRun](./Models/ScenarioLastRun.md)
 - [ScenarioRun](./Models/ScenarioRun.md)
 - [ScenarioRunContainer](./Models/ScenarioRunContainer.md)
 - [ScenarioRunContainerArtifact](./Models/ScenarioRunContainerArtifact.md)
 - [ScenarioRunContainerLogs](./Models/ScenarioRunContainerLogs.md)
 - [ScenarioRunLogs](./Models/ScenarioRunLogs.md)
 - [ScenarioRunSearch](./Models/ScenarioRunSearch.md)
 - [ScenarioRunStartContainers](./Models/ScenarioRunStartContainers.md)
 - [ScenarioRunState](./Models/ScenarioRunState.md)
 - [ScenarioRunStatus](./Models/ScenarioRunStatus.md)
 - [ScenarioRunStatusNode](./Models/ScenarioRunStatusNode.md)
 - [ScenarioRunTemplateParameterValue](./Models/ScenarioRunTemplateParameterValue.md)
 - [ScenarioUser](./Models/ScenarioUser.md)
 - [ScenarioValidationStatus](./Models/ScenarioValidationStatus.md)
 - [Solution](./Models/Solution.md)
 - [User](./Models/User.md)
 - [UserOrganization](./Models/UserOrganization.md)
 - [UserWorkspace](./Models/UserWorkspace.md)
 - [Validator](./Models/Validator.md)
 - [ValidatorRun](./Models/ValidatorRun.md)
 - [Workspace](./Models/Workspace.md)
 - [WorkspaceAccessControl](./Models/WorkspaceAccessControl.md)
 - [WorkspaceAccessControlWithPermissions](./Models/WorkspaceAccessControlWithPermissions.md)
 - [WorkspaceAccessControlWithPermissions_allOf](./Models/WorkspaceAccessControlWithPermissions_allOf.md)
 - [WorkspaceFile](./Models/WorkspaceFile.md)
 - [WorkspaceRole](./Models/WorkspaceRole.md)
 - [WorkspaceRoleItems](./Models/WorkspaceRoleItems.md)
 - [WorkspaceSecurity](./Models/WorkspaceSecurity.md)
 - [WorkspaceSolution](./Models/WorkspaceSolution.md)
 - [WorkspaceWebApp](./Models/WorkspaceWebApp.md)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

<a name="oAuth2AuthCode"></a>
### oAuth2AuthCode

- **Type**: OAuth
- **Flow**: implicit
- **Authorization URL**: https://login.microsoftonline.com/common/oauth2/v2.0/authorize
- **Scopes**: 
  - http://dev.api.cosmotech.com/platform: Platform scope

