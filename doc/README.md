# Documentation for Cosmo Tech Platform API

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *https://dev.api.cosmotech.com*

| Class | Method | HTTP request | Description |
|------------ | ------------- | ------------- | -------------|
| *ConnectorApi* | [**findAllConnectors**](Apis/ConnectorApi.md#findallconnectors) | **GET** /connectors | List all Connectors |
*ConnectorApi* | [**findConnectorById**](Apis/ConnectorApi.md#findconnectorbyid) | **GET** /connectors/{connector_id} | Get the details of a connector |
*ConnectorApi* | [**findConnectorByName**](Apis/ConnectorApi.md#findconnectorbyname) | **GET** /connectors/name/{connector_name} | Get the details of a connector |
*ConnectorApi* | [**registerConnector**](Apis/ConnectorApi.md#registerconnector) | **POST** /connectors | Register a new connector |
*ConnectorApi* | [**unregisterConnector**](Apis/ConnectorApi.md#unregisterconnector) | **DELETE** /connectors/{connector_id} | Unregister a connector |
| *DatasetApi* | [**addDatasetAccessControl**](Apis/DatasetApi.md#adddatasetaccesscontrol) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/security/access | Add a control access to the Dataset |
*DatasetApi* | [**addOrReplaceDatasetCompatibilityElements**](Apis/DatasetApi.md#addorreplacedatasetcompatibilityelements) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Add Dataset Compatibility elements. |
*DatasetApi* | [**copyDataset**](Apis/DatasetApi.md#copydataset) | **POST** /organizations/{organization_id}/datasets/copy | Copy a Dataset to another Dataset. |
*DatasetApi* | [**createDataset**](Apis/DatasetApi.md#createdataset) | **POST** /organizations/{organization_id}/datasets | Create a new Dataset |
*DatasetApi* | [**createSubDataset**](Apis/DatasetApi.md#createsubdataset) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/subdataset | Create a sub-dataset from the dataset in parameter |
*DatasetApi* | [**createTwingraphEntities**](Apis/DatasetApi.md#createtwingraphentities) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Create new entities in a graph instance |
*DatasetApi* | [**deleteDataset**](Apis/DatasetApi.md#deletedataset) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id} | Delete a dataset |
*DatasetApi* | [**deleteTwingraphEntities**](Apis/DatasetApi.md#deletetwingraphentities) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Delete entities in a graph instance |
*DatasetApi* | [**downloadTwingraph**](Apis/DatasetApi.md#downloadtwingraph) | **GET** /organizations/{organization_id}/datasets/twingraph/download/{hash} | Download a graph as a zip file |
*DatasetApi* | [**findAllDatasets**](Apis/DatasetApi.md#findalldatasets) | **GET** /organizations/{organization_id}/datasets | List all Datasets |
*DatasetApi* | [**findDatasetById**](Apis/DatasetApi.md#finddatasetbyid) | **GET** /organizations/{organization_id}/datasets/{dataset_id} | Get the details of a Dataset |
*DatasetApi* | [**getDatasetAccessControl**](Apis/DatasetApi.md#getdatasetaccesscontrol) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/security/access/{identity_id} | Get a control access for the Dataset |
*DatasetApi* | [**getDatasetSecurity**](Apis/DatasetApi.md#getdatasetsecurity) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/security | Get the Dataset security information |
*DatasetApi* | [**getDatasetSecurityUsers**](Apis/DatasetApi.md#getdatasetsecurityusers) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/security/users | Get the Dataset security users list |
*DatasetApi* | [**getDatasetTwingraphStatus**](Apis/DatasetApi.md#getdatasettwingraphstatus) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/status | Get the dataset's refresh job status |
*DatasetApi* | [**getTwingraphEntities**](Apis/DatasetApi.md#gettwingraphentities) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Get entities in a graph instance |
*DatasetApi* | [**linkWorkspace**](Apis/DatasetApi.md#linkworkspace) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/link |  |
*DatasetApi* | [**refreshDataset**](Apis/DatasetApi.md#refreshdataset) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/refresh | Refresh data on dataset from dataset's source |
*DatasetApi* | [**removeAllDatasetCompatibilityElements**](Apis/DatasetApi.md#removealldatasetcompatibilityelements) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Remove all Dataset Compatibility elements from the Dataset specified |
*DatasetApi* | [**removeDatasetAccessControl**](Apis/DatasetApi.md#removedatasetaccesscontrol) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/security/access/{identity_id} | Remove the specified access from the given Dataset |
*DatasetApi* | [**rollbackRefresh**](Apis/DatasetApi.md#rollbackrefresh) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/refresh/rollback | Rollback the dataset after a failed refresh |
*DatasetApi* | [**searchDatasets**](Apis/DatasetApi.md#searchdatasets) | **POST** /organizations/{organization_id}/datasets/search | Search Datasets by tags |
*DatasetApi* | [**setDatasetDefaultSecurity**](Apis/DatasetApi.md#setdatasetdefaultsecurity) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/security/default | Set the Dataset default security |
*DatasetApi* | [**twingraphBatchQuery**](Apis/DatasetApi.md#twingraphbatchquery) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/batch-query | Run a query on a graph instance and return the result as a zip file in async mode |
*DatasetApi* | [**twingraphBatchUpdate**](Apis/DatasetApi.md#twingraphbatchupdate) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/batch | Async batch update by loading a CSV file on a graph instance |
*DatasetApi* | [**twingraphQuery**](Apis/DatasetApi.md#twingraphquery) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/twingraph | Return the result of a query made on the graph instance as a json |
*DatasetApi* | [**unlinkWorkspace**](Apis/DatasetApi.md#unlinkworkspace) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/unlink |  |
*DatasetApi* | [**updateDataset**](Apis/DatasetApi.md#updatedataset) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id} | Update a dataset |
*DatasetApi* | [**updateDatasetAccessControl**](Apis/DatasetApi.md#updatedatasetaccesscontrol) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id}/security/access/{identity_id} | Update the specified access to User for a Dataset |
*DatasetApi* | [**updateTwingraphEntities**](Apis/DatasetApi.md#updatetwingraphentities) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Update entities in a graph instance |
*DatasetApi* | [**uploadTwingraph**](Apis/DatasetApi.md#uploadtwingraph) | **POST** /organizations/{organization_id}/datasets/{dataset_id} | Upload data from zip file to dataset's twingraph |
| *OrganizationApi* | [**addOrganizationAccessControl**](Apis/OrganizationApi.md#addorganizationaccesscontrol) | **POST** /organizations/{organization_id}/security/access | Add a control access to the Organization |
*OrganizationApi* | [**findAllOrganizations**](Apis/OrganizationApi.md#findallorganizations) | **GET** /organizations | List all Organizations |
*OrganizationApi* | [**findOrganizationById**](Apis/OrganizationApi.md#findorganizationbyid) | **GET** /organizations/{organization_id} | Get the details of an Organization |
*OrganizationApi* | [**getAllPermissions**](Apis/OrganizationApi.md#getallpermissions) | **GET** /organizations/permissions | Get all permissions per components |
*OrganizationApi* | [**getOrganizationAccessControl**](Apis/OrganizationApi.md#getorganizationaccesscontrol) | **GET** /organizations/{organization_id}/security/access/{identity_id} | Get a control access for the Organization |
*OrganizationApi* | [**getOrganizationPermissions**](Apis/OrganizationApi.md#getorganizationpermissions) | **GET** /organizations/{organization_id}/permissions/{role} | Get the Organization permissions by given role |
*OrganizationApi* | [**getOrganizationSecurity**](Apis/OrganizationApi.md#getorganizationsecurity) | **GET** /organizations/{organization_id}/security | Get the Organization security information |
*OrganizationApi* | [**getOrganizationSecurityUsers**](Apis/OrganizationApi.md#getorganizationsecurityusers) | **GET** /organizations/{organization_id}/security/users | Get the Organization security users list |
*OrganizationApi* | [**registerOrganization**](Apis/OrganizationApi.md#registerorganization) | **POST** /organizations | Register a new organization |
*OrganizationApi* | [**removeOrganizationAccessControl**](Apis/OrganizationApi.md#removeorganizationaccesscontrol) | **DELETE** /organizations/{organization_id}/security/access/{identity_id} | Remove the specified access from the given Organization |
*OrganizationApi* | [**setOrganizationDefaultSecurity**](Apis/OrganizationApi.md#setorganizationdefaultsecurity) | **POST** /organizations/{organization_id}/security/default | Set the Organization default security |
*OrganizationApi* | [**unregisterOrganization**](Apis/OrganizationApi.md#unregisterorganization) | **DELETE** /organizations/{organization_id} | Unregister an organization |
*OrganizationApi* | [**updateOrganization**](Apis/OrganizationApi.md#updateorganization) | **PATCH** /organizations/{organization_id} | Update an Organization |
*OrganizationApi* | [**updateOrganizationAccessControl**](Apis/OrganizationApi.md#updateorganizationaccesscontrol) | **PATCH** /organizations/{organization_id}/security/access/{identity_id} | Update the specified access to User for an Organization |
*OrganizationApi* | [**updateSolutionsContainerRegistryByOrganizationId**](Apis/OrganizationApi.md#updatesolutionscontainerregistrybyorganizationid) | **PATCH** /organizations/{organization_id}/services/solutionsContainerRegistry | Update the solutions container registry configuration for the Organization specified |
*OrganizationApi* | [**updateStorageByOrganizationId**](Apis/OrganizationApi.md#updatestoragebyorganizationid) | **PATCH** /organizations/{organization_id}/services/storage | Update storage configuration for the Organization specified |
*OrganizationApi* | [**updateTenantCredentialsByOrganizationId**](Apis/OrganizationApi.md#updatetenantcredentialsbyorganizationid) | **PATCH** /organizations/{organization_id}/services/tenantCredentials | Update tenant credentials for the Organization specified |
| *RunApi* | [**deleteRun**](Apis/RunApi.md#deleterun) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id} | Delete a run |
*RunApi* | [**getRun**](Apis/RunApi.md#getrun) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id} | Get the details of a run |
*RunApi* | [**getRunLogs**](Apis/RunApi.md#getrunlogs) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/logs | get the logs for the Run |
*RunApi* | [**getRunStatus**](Apis/RunApi.md#getrunstatus) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/status | get the status for the Run |
*RunApi* | [**listRuns**](Apis/RunApi.md#listruns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs | get the list of Runs for the Runner |
*RunApi* | [**queryRunData**](Apis/RunApi.md#queryrundata) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/data/query | query the run data |
*RunApi* | [**sendRunData**](Apis/RunApi.md#sendrundata) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/data/send | Send data associated to a run |
| *RunnerApi* | [**addRunnerAccessControl**](Apis/RunnerApi.md#addrunneraccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access | Add a control access to the Runner |
*RunnerApi* | [**createRunner**](Apis/RunnerApi.md#createrunner) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners | Create a new Runner |
*RunnerApi* | [**deleteRunner**](Apis/RunnerApi.md#deleterunner) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Delete a runner |
*RunnerApi* | [**getRunner**](Apis/RunnerApi.md#getrunner) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Get the details of an runner |
*RunnerApi* | [**getRunnerAccessControl**](Apis/RunnerApi.md#getrunneraccesscontrol) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Get a control access for the Runner |
*RunnerApi* | [**getRunnerPermissions**](Apis/RunnerApi.md#getrunnerpermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/permissions/{role} | Get the Runner permission by given role |
*RunnerApi* | [**getRunnerSecurity**](Apis/RunnerApi.md#getrunnersecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security | Get the Runner security information |
*RunnerApi* | [**getRunnerSecurityUsers**](Apis/RunnerApi.md#getrunnersecurityusers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/users | Get the Runner security users list |
*RunnerApi* | [**listRunners**](Apis/RunnerApi.md#listrunners) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners | List all Runners |
*RunnerApi* | [**removeRunnerAccessControl**](Apis/RunnerApi.md#removerunneraccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Remove the specified access from the given Organization Runner |
*RunnerApi* | [**setRunnerDefaultSecurity**](Apis/RunnerApi.md#setrunnerdefaultsecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/default | Set the Runner default security |
*RunnerApi* | [**startRun**](Apis/RunnerApi.md#startrun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/start | Start a run with runner parameters |
*RunnerApi* | [**stopRun**](Apis/RunnerApi.md#stoprun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/stop | Stop the last run |
*RunnerApi* | [**updateRunner**](Apis/RunnerApi.md#updaterunner) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Update a runner |
*RunnerApi* | [**updateRunnerAccessControl**](Apis/RunnerApi.md#updaterunneraccesscontrol) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Update the specified access to User for a Runner |
| *ScenarioApi* | [**addOrReplaceScenarioParameterValues**](Apis/ScenarioApi.md#addorreplacescenarioparametervalues) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/parameterValues | Add (or replace) Parameter Values for the Scenario specified |
*ScenarioApi* | [**addScenarioAccessControl**](Apis/ScenarioApi.md#addscenarioaccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access | Add a control access to the Scenario |
*ScenarioApi* | [**compareScenarios**](Apis/ScenarioApi.md#comparescenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/compare/{compared_scenario_id} | Compare the Scenario with another one and returns the difference for parameters values |
*ScenarioApi* | [**createScenario**](Apis/ScenarioApi.md#createscenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Create a new Scenario |
*ScenarioApi* | [**deleteAllScenarios**](Apis/ScenarioApi.md#deleteallscenarios) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Delete all Scenarios of the Workspace |
*ScenarioApi* | [**deleteScenario**](Apis/ScenarioApi.md#deletescenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Delete a scenario |
*ScenarioApi* | [**downloadScenarioData**](Apis/ScenarioApi.md#downloadscenariodata) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/downloads | Download Scenario data |
*ScenarioApi* | [**findAllScenarios**](Apis/ScenarioApi.md#findallscenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | List all Scenarios |
*ScenarioApi* | [**findAllScenariosByValidationStatus**](Apis/ScenarioApi.md#findallscenariosbyvalidationstatus) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/{validationStatus} | List all Scenarios by validation status |
*ScenarioApi* | [**findScenarioById**](Apis/ScenarioApi.md#findscenariobyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Get the details of an scenario |
*ScenarioApi* | [**getScenarioAccessControl**](Apis/ScenarioApi.md#getscenarioaccesscontrol) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access/{identity_id} | Get a control access for the Scenario |
*ScenarioApi* | [**getScenarioDataDownloadJobInfo**](Apis/ScenarioApi.md#getscenariodatadownloadjobinfo) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/downloads/{download_id} | Get Scenario data download URL |
*ScenarioApi* | [**getScenarioPermissions**](Apis/ScenarioApi.md#getscenariopermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/permissions/{role} | Get the Scenario permission by given role |
*ScenarioApi* | [**getScenarioSecurity**](Apis/ScenarioApi.md#getscenariosecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security | Get the Scenario security information |
*ScenarioApi* | [**getScenarioSecurityUsers**](Apis/ScenarioApi.md#getscenariosecurityusers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/users | Get the Scenario security users list |
*ScenarioApi* | [**getScenarioValidationStatusById**](Apis/ScenarioApi.md#getscenariovalidationstatusbyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/ValidationStatus | Get the validation status of an scenario |
*ScenarioApi* | [**getScenariosTree**](Apis/ScenarioApi.md#getscenariostree) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/tree | Get the Scenarios Tree |
*ScenarioApi* | [**removeAllScenarioParameterValues**](Apis/ScenarioApi.md#removeallscenarioparametervalues) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/parameterValues | Remove all Parameter Values from the Scenario specified |
*ScenarioApi* | [**removeScenarioAccessControl**](Apis/ScenarioApi.md#removescenarioaccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access/{identity_id} | Remove the specified access from the given Organization Scenario |
*ScenarioApi* | [**setScenarioDefaultSecurity**](Apis/ScenarioApi.md#setscenariodefaultsecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/default | Set the Scenario default security |
*ScenarioApi* | [**updateScenario**](Apis/ScenarioApi.md#updatescenario) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Update a scenario |
*ScenarioApi* | [**updateScenarioAccessControl**](Apis/ScenarioApi.md#updatescenarioaccesscontrol) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access/{identity_id} | Update the specified access to User for a Scenario |
| *ScenariorunApi* | [**deleteHistoricalDataOrganization**](Apis/ScenariorunApi.md#deletehistoricaldataorganization) | **DELETE** /organizations/{organization_id}/scenarioruns/historicaldata | Delete all historical ScenarioRuns in the Organization |
*ScenariorunApi* | [**deleteHistoricalDataScenario**](Apis/ScenariorunApi.md#deletehistoricaldatascenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns/historicaldata | Delete all historical ScenarioRuns in the Scenario |
*ScenariorunApi* | [**deleteHistoricalDataWorkspace**](Apis/ScenariorunApi.md#deletehistoricaldataworkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarioruns/historicaldata | Delete all historical ScenarioRuns in the Workspace |
*ScenariorunApi* | [**deleteScenarioRun**](Apis/ScenariorunApi.md#deletescenariorun) | **DELETE** /organizations/{organization_id}/scenarioruns/{scenariorun_id} | Delete a scenariorun |
*ScenariorunApi* | [**findScenarioRunById**](Apis/ScenariorunApi.md#findscenariorunbyid) | **GET** /organizations/{organization_id}/scenarioruns/{scenariorun_id} | Get the details of a scenariorun |
*ScenariorunApi* | [**getScenarioRunCumulatedLogs**](Apis/ScenariorunApi.md#getscenarioruncumulatedlogs) | **GET** /organizations/{organization_id}/scenarioruns/{scenariorun_id}/cumulatedlogs | Get the cumulated logs of a scenariorun |
*ScenariorunApi* | [**getScenarioRunLogs**](Apis/ScenariorunApi.md#getscenariorunlogs) | **GET** /organizations/{organization_id}/scenarioruns/{scenariorun_id}/logs | get the logs for the ScenarioRun |
*ScenariorunApi* | [**getScenarioRunStatus**](Apis/ScenariorunApi.md#getscenariorunstatus) | **GET** /organizations/{organization_id}/scenarioruns/{scenariorun_id}/status | get the status for the ScenarioRun |
*ScenariorunApi* | [**getScenarioRuns**](Apis/ScenariorunApi.md#getscenarioruns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns | get the list of ScenarioRuns for the Scenario |
*ScenariorunApi* | [**getWorkspaceScenarioRuns**](Apis/ScenariorunApi.md#getworkspacescenarioruns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarioruns | get the list of ScenarioRuns for the Workspace |
*ScenariorunApi* | [**runScenario**](Apis/ScenariorunApi.md#runscenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/run | run a ScenarioRun for the Scenario |
*ScenariorunApi* | [**searchScenarioRuns**](Apis/ScenariorunApi.md#searchscenarioruns) | **POST** /organizations/{organization_id}/scenarioruns/search | Search ScenarioRuns |
*ScenariorunApi* | [**startScenarioRunContainers**](Apis/ScenariorunApi.md#startscenarioruncontainers) | **POST** /organizations/{organization_id}/scenarioruns/startcontainers | Start a new scenariorun with raw containers definition |
*ScenariorunApi* | [**stopScenarioRun**](Apis/ScenariorunApi.md#stopscenariorun) | **POST** /organizations/{organization_id}/scenarioruns/{scenariorun_id}/stop | stop a ScenarioRun for the Scenario |
| *SolutionApi* | [**addOrReplaceParameterGroups**](Apis/SolutionApi.md#addorreplaceparametergroups) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Add Parameter Groups. Any item with the same ID will be overwritten |
*SolutionApi* | [**addOrReplaceParameters**](Apis/SolutionApi.md#addorreplaceparameters) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameters | Add Parameters. Any item with the same ID will be overwritten |
*SolutionApi* | [**addOrReplaceRunTemplates**](Apis/SolutionApi.md#addorreplaceruntemplates) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Add Run Templates. Any item with the same ID will be overwritten |
*SolutionApi* | [**addSolutionAccessControl**](Apis/SolutionApi.md#addsolutionaccesscontrol) | **POST** /organizations/{organization_id}/solutions/{solution_id}/security/access | Add a control access to the Solution |
*SolutionApi* | [**createSolution**](Apis/SolutionApi.md#createsolution) | **POST** /organizations/{organization_id}/solutions | Register a new solution |
*SolutionApi* | [**deleteSolution**](Apis/SolutionApi.md#deletesolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution |
*SolutionApi* | [**deleteSolutionRunTemplate**](Apis/SolutionApi.md#deletesolutionruntemplate) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Remove the specified Solution Run Template |
*SolutionApi* | [**downloadRunTemplateHandler**](Apis/SolutionApi.md#downloadruntemplatehandler) | **GET** /organizations/{organization_id}/solutions/{solution_id}/runtemplates/{run_template_id}/handlers/{handler_id}/download | Download a Run Template step handler zip file |
*SolutionApi* | [**findAllSolutions**](Apis/SolutionApi.md#findallsolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions |
*SolutionApi* | [**findSolutionById**](Apis/SolutionApi.md#findsolutionbyid) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution |
*SolutionApi* | [**getSolutionAccessControl**](Apis/SolutionApi.md#getsolutionaccesscontrol) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Get a control access for the Solution |
*SolutionApi* | [**getSolutionSecurity**](Apis/SolutionApi.md#getsolutionsecurity) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security | Get the Solution security information |
*SolutionApi* | [**getSolutionSecurityUsers**](Apis/SolutionApi.md#getsolutionsecurityusers) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/users | Get the Solution security users list |
*SolutionApi* | [**removeAllRunTemplates**](Apis/SolutionApi.md#removeallruntemplates) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Remove all Run Templates from the Solution specified |
*SolutionApi* | [**removeAllSolutionParameterGroups**](Apis/SolutionApi.md#removeallsolutionparametergroups) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Remove all Parameter Groups from the Solution specified |
*SolutionApi* | [**removeAllSolutionParameters**](Apis/SolutionApi.md#removeallsolutionparameters) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameters | Remove all Parameters from the Solution specified |
*SolutionApi* | [**removeSolutionAccessControl**](Apis/SolutionApi.md#removesolutionaccesscontrol) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Remove the specified access from the given Organization Solution |
*SolutionApi* | [**setSolutionDefaultSecurity**](Apis/SolutionApi.md#setsolutiondefaultsecurity) | **POST** /organizations/{organization_id}/solutions/{solution_id}/security/default | Set the Solution default security |
*SolutionApi* | [**updateSolution**](Apis/SolutionApi.md#updatesolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution |
*SolutionApi* | [**updateSolutionAccessControl**](Apis/SolutionApi.md#updatesolutionaccesscontrol) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Update the specified access to User for a Solution |
*SolutionApi* | [**updateSolutionRunTemplate**](Apis/SolutionApi.md#updatesolutionruntemplate) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Update the specified Solution Run Template |
*SolutionApi* | [**uploadRunTemplateHandler**](Apis/SolutionApi.md#uploadruntemplatehandler) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runtemplates/{run_template_id}/handlers/{handler_id}/upload | Upload a Run Template step handler zip file |
| *TwingraphApi* | [**batchQuery**](Apis/TwingraphApi.md#batchquery) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/batch-query | Run a query on a graph instance and return the result as a zip file in async mode |
*TwingraphApi* | [**batchUploadUpdate**](Apis/TwingraphApi.md#batchuploadupdate) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/batch | Async batch update by loading a CSV file on a graph instance  |
*TwingraphApi* | [**createEntities**](Apis/TwingraphApi.md#createentities) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Create new entities in a graph instance |
*TwingraphApi* | [**createGraph**](Apis/TwingraphApi.md#creategraph) | **POST** /organizations/{organization_id}/twingraph/{graph_id} | Create a new graph |
*TwingraphApi* | [**delete**](Apis/TwingraphApi.md#delete) | **DELETE** /organizations/{organization_id}/twingraph/{graph_id} | Delete all versions of a graph and his metadatas |
*TwingraphApi* | [**deleteEntities**](Apis/TwingraphApi.md#deleteentities) | **DELETE** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Delete entities in a graph instance |
*TwingraphApi* | [**downloadGraph**](Apis/TwingraphApi.md#downloadgraph) | **GET** /organizations/{organization_id}/twingraph/download/{hash} | Download a graph compressed in a zip file |
*TwingraphApi* | [**findAllTwingraphs**](Apis/TwingraphApi.md#findalltwingraphs) | **GET** /organizations/{organization_id}/twingraphs | Return the list of all graphs stored in the organization |
*TwingraphApi* | [**getEntities**](Apis/TwingraphApi.md#getentities) | **GET** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Get entities in a graph instance |
*TwingraphApi* | [**getGraphMetaData**](Apis/TwingraphApi.md#getgraphmetadata) | **GET** /organizations/{organization_id}/twingraph/{graph_id}/metadata | Return the metaData of the specified graph |
*TwingraphApi* | [**jobStatus**](Apis/TwingraphApi.md#jobstatus) | **GET** /organizations/{organization_id}/job/{job_id}/status | Get the status of a job |
*TwingraphApi* | [**query**](Apis/TwingraphApi.md#query) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/query | Run a query on a graph instance |
*TwingraphApi* | [**updateEntities**](Apis/TwingraphApi.md#updateentities) | **PATCH** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Update entities in a graph instance |
*TwingraphApi* | [**updateGraphMetaData**](Apis/TwingraphApi.md#updategraphmetadata) | **PATCH** /organizations/{organization_id}/twingraph/{graph_id}/metadata | Update the metaData of the specified graph |
| *WorkspaceApi* | [**addWorkspaceAccessControl**](Apis/WorkspaceApi.md#addworkspaceaccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/access | Add a control access to the Workspace |
*WorkspaceApi* | [**createSecret**](Apis/WorkspaceApi.md#createsecret) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/secret | Create a secret for the Workspace |
*WorkspaceApi* | [**createWorkspace**](Apis/WorkspaceApi.md#createworkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace |
*WorkspaceApi* | [**deleteAllWorkspaceFiles**](Apis/WorkspaceApi.md#deleteallworkspacefiles) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files | Delete all Workspace files |
*WorkspaceApi* | [**deleteWorkspace**](Apis/WorkspaceApi.md#deleteworkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace |
*WorkspaceApi* | [**deleteWorkspaceFile**](Apis/WorkspaceApi.md#deleteworkspacefile) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files/delete | Delete a workspace file |
*WorkspaceApi* | [**downloadWorkspaceFile**](Apis/WorkspaceApi.md#downloadworkspacefile) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files/download | Download the Workspace File specified |
*WorkspaceApi* | [**findAllWorkspaceFiles**](Apis/WorkspaceApi.md#findallworkspacefiles) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files | List all Workspace files |
*WorkspaceApi* | [**findAllWorkspaces**](Apis/WorkspaceApi.md#findallworkspaces) | **GET** /organizations/{organization_id}/workspaces | List all Workspaces |
*WorkspaceApi* | [**findWorkspaceById**](Apis/WorkspaceApi.md#findworkspacebyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of an workspace |
*WorkspaceApi* | [**getWorkspaceAccessControl**](Apis/WorkspaceApi.md#getworkspaceaccesscontrol) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Get a control access for the Workspace |
*WorkspaceApi* | [**getWorkspacePermissions**](Apis/WorkspaceApi.md#getworkspacepermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/permissions/{role} | Get the Workspace permission by given role |
*WorkspaceApi* | [**getWorkspaceSecurity**](Apis/WorkspaceApi.md#getworkspacesecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security | Get the Workspace security information |
*WorkspaceApi* | [**getWorkspaceSecurityUsers**](Apis/WorkspaceApi.md#getworkspacesecurityusers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/users | Get the Workspace security users list |
*WorkspaceApi* | [**linkDataset**](Apis/WorkspaceApi.md#linkdataset) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/link |  |
*WorkspaceApi* | [**removeWorkspaceAccessControl**](Apis/WorkspaceApi.md#removeworkspaceaccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Remove the specified access from the given Organization Workspace |
*WorkspaceApi* | [**setWorkspaceDefaultSecurity**](Apis/WorkspaceApi.md#setworkspacedefaultsecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/default | Set the Workspace default security |
*WorkspaceApi* | [**unlinkDataset**](Apis/WorkspaceApi.md#unlinkdataset) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/unlink |  |
*WorkspaceApi* | [**updateWorkspace**](Apis/WorkspaceApi.md#updateworkspace) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id} | Update a workspace |
*WorkspaceApi* | [**updateWorkspaceAccessControl**](Apis/WorkspaceApi.md#updateworkspaceaccesscontrol) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Update the specified access to User for a Workspace |
*WorkspaceApi* | [**uploadWorkspaceFile**](Apis/WorkspaceApi.md#uploadworkspacefile) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/files | Upload a file for the Workspace |


<a name="documentation-for-models"></a>
## Documentation for Models

 - [ComponentRolePermissions](./Models/ComponentRolePermissions.md)
 - [Connector](./Models/Connector.md)
 - [ConnectorParameter](./Models/ConnectorParameter.md)
 - [ConnectorParameterGroup](./Models/ConnectorParameterGroup.md)
 - [ContainerResourceSizeInfo](./Models/ContainerResourceSizeInfo.md)
 - [ContainerResourceSizing](./Models/ContainerResourceSizing.md)
 - [Dataset](./Models/Dataset.md)
 - [DatasetAccessControl](./Models/DatasetAccessControl.md)
 - [DatasetCompatibility](./Models/DatasetCompatibility.md)
 - [DatasetCopyParameters](./Models/DatasetCopyParameters.md)
 - [DatasetRole](./Models/DatasetRole.md)
 - [DatasetSearch](./Models/DatasetSearch.md)
 - [DatasetSecurity](./Models/DatasetSecurity.md)
 - [DatasetSourceType](./Models/DatasetSourceType.md)
 - [DatasetTwinGraphHash](./Models/DatasetTwinGraphHash.md)
 - [DatasetTwinGraphInfo](./Models/DatasetTwinGraphInfo.md)
 - [DatasetTwinGraphQuery](./Models/DatasetTwinGraphQuery.md)
 - [Dataset_connector](./Models/Dataset_connector.md)
 - [DeleteHistoricalData](./Models/DeleteHistoricalData.md)
 - [FileUploadMetadata](./Models/FileUploadMetadata.md)
 - [FileUploadValidation](./Models/FileUploadValidation.md)
 - [GraphProperties](./Models/GraphProperties.md)
 - [IngestionStatusEnum](./Models/IngestionStatusEnum.md)
 - [Organization](./Models/Organization.md)
 - [OrganizationAccessControl](./Models/OrganizationAccessControl.md)
 - [OrganizationRole](./Models/OrganizationRole.md)
 - [OrganizationSecurity](./Models/OrganizationSecurity.md)
 - [OrganizationService](./Models/OrganizationService.md)
 - [OrganizationServices](./Models/OrganizationServices.md)
 - [QueryResult](./Models/QueryResult.md)
 - [ResourceSizeInfo](./Models/ResourceSizeInfo.md)
 - [Run](./Models/Run.md)
 - [RunContainer](./Models/RunContainer.md)
 - [RunContainerArtifact](./Models/RunContainerArtifact.md)
 - [RunContainerLogs](./Models/RunContainerLogs.md)
 - [RunData](./Models/RunData.md)
 - [RunDataQuery](./Models/RunDataQuery.md)
 - [RunLogs](./Models/RunLogs.md)
 - [RunResourceRequested](./Models/RunResourceRequested.md)
 - [RunSearch](./Models/RunSearch.md)
 - [RunSearchState](./Models/RunSearchState.md)
 - [RunStartContainers](./Models/RunStartContainers.md)
 - [RunState](./Models/RunState.md)
 - [RunStatus](./Models/RunStatus.md)
 - [RunStatusNode](./Models/RunStatusNode.md)
 - [RunTemplate](./Models/RunTemplate.md)
 - [RunTemplateHandlerId](./Models/RunTemplateHandlerId.md)
 - [RunTemplateOrchestrator](./Models/RunTemplateOrchestrator.md)
 - [RunTemplateParameter](./Models/RunTemplateParameter.md)
 - [RunTemplateParameterGroup](./Models/RunTemplateParameterGroup.md)
 - [RunTemplateParameterValue](./Models/RunTemplateParameterValue.md)
 - [RunTemplateResourceSizing](./Models/RunTemplateResourceSizing.md)
 - [RunTemplateStepSource](./Models/RunTemplateStepSource.md)
 - [Runner](./Models/Runner.md)
 - [RunnerAccessControl](./Models/RunnerAccessControl.md)
 - [RunnerChangedParameterValue](./Models/RunnerChangedParameterValue.md)
 - [RunnerComparisonResult](./Models/RunnerComparisonResult.md)
 - [RunnerDataDownloadInfo](./Models/RunnerDataDownloadInfo.md)
 - [RunnerDataDownloadJob](./Models/RunnerDataDownloadJob.md)
 - [RunnerJobState](./Models/RunnerJobState.md)
 - [RunnerLastRun](./Models/RunnerLastRun.md)
 - [RunnerResourceSizing](./Models/RunnerResourceSizing.md)
 - [RunnerRole](./Models/RunnerRole.md)
 - [RunnerRunTemplateParameterValue](./Models/RunnerRunTemplateParameterValue.md)
 - [RunnerSecurity](./Models/RunnerSecurity.md)
 - [RunnerValidationStatus](./Models/RunnerValidationStatus.md)
 - [Runner_lastRun](./Models/Runner_lastRun.md)
 - [Runner_parentLastRun](./Models/Runner_parentLastRun.md)
 - [Runner_rootLastRun](./Models/Runner_rootLastRun.md)
 - [Scenario](./Models/Scenario.md)
 - [ScenarioAccessControl](./Models/ScenarioAccessControl.md)
 - [ScenarioChangedParameterValue](./Models/ScenarioChangedParameterValue.md)
 - [ScenarioComparisonResult](./Models/ScenarioComparisonResult.md)
 - [ScenarioDataDownloadInfo](./Models/ScenarioDataDownloadInfo.md)
 - [ScenarioDataDownloadJob](./Models/ScenarioDataDownloadJob.md)
 - [ScenarioJobState](./Models/ScenarioJobState.md)
 - [ScenarioLastRun](./Models/ScenarioLastRun.md)
 - [ScenarioResourceSizing](./Models/ScenarioResourceSizing.md)
 - [ScenarioRole](./Models/ScenarioRole.md)
 - [ScenarioRun](./Models/ScenarioRun.md)
 - [ScenarioRunContainer](./Models/ScenarioRunContainer.md)
 - [ScenarioRunContainerArtifact](./Models/ScenarioRunContainerArtifact.md)
 - [ScenarioRunContainerLogs](./Models/ScenarioRunContainerLogs.md)
 - [ScenarioRunLogs](./Models/ScenarioRunLogs.md)
 - [ScenarioRunResourceRequested](./Models/ScenarioRunResourceRequested.md)
 - [ScenarioRunSearch](./Models/ScenarioRunSearch.md)
 - [ScenarioRunSearchState](./Models/ScenarioRunSearchState.md)
 - [ScenarioRunStartContainers](./Models/ScenarioRunStartContainers.md)
 - [ScenarioRunState](./Models/ScenarioRunState.md)
 - [ScenarioRunStatus](./Models/ScenarioRunStatus.md)
 - [ScenarioRunStatusNode](./Models/ScenarioRunStatusNode.md)
 - [ScenarioRunTemplateParameterValue](./Models/ScenarioRunTemplateParameterValue.md)
 - [ScenarioSecurity](./Models/ScenarioSecurity.md)
 - [ScenarioValidationStatus](./Models/ScenarioValidationStatus.md)
 - [Solution](./Models/Solution.md)
 - [SolutionAccessControl](./Models/SolutionAccessControl.md)
 - [SolutionRole](./Models/SolutionRole.md)
 - [SolutionSecurity](./Models/SolutionSecurity.md)
 - [SourceInfo](./Models/SourceInfo.md)
 - [SubDatasetGraphQuery](./Models/SubDatasetGraphQuery.md)
 - [TwinGraphBatchResult](./Models/TwinGraphBatchResult.md)
 - [TwinGraphHash](./Models/TwinGraphHash.md)
 - [TwinGraphQuery](./Models/TwinGraphQuery.md)
 - [TwincacheStatusEnum](./Models/TwincacheStatusEnum.md)
 - [Validator](./Models/Validator.md)
 - [Workspace](./Models/Workspace.md)
 - [WorkspaceAccessControl](./Models/WorkspaceAccessControl.md)
 - [WorkspaceFile](./Models/WorkspaceFile.md)
 - [WorkspaceRole](./Models/WorkspaceRole.md)
 - [WorkspaceSecret](./Models/WorkspaceSecret.md)
 - [WorkspaceSecurity](./Models/WorkspaceSecurity.md)
 - [WorkspaceSolution](./Models/WorkspaceSolution.md)
 - [WorkspaceWebApp](./Models/WorkspaceWebApp.md)
 - [ioTypesEnum](./Models/ioTypesEnum.md)
 - [sendRunData_request](./Models/sendRunData_request.md)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

<a name="oAuth2AuthCode"></a>
### oAuth2AuthCode

- **Type**: OAuth
- **Flow**: implicit
- **Authorization URL**: https://login.microsoftonline.com/common/oauth2/v2.0/authorize
- **Scopes**: 
  - http://dev.api.cosmotech.com/platform: Platform scope

