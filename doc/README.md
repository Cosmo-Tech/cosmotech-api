# Documentation for Cosmo Tech Platform API

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *https://dev.api.cosmotech.com*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ConnectorApi* | [**findAllConnectors**](Apis/ConnectorApi.md#findallconnectors) | **GET** /connectors | List all Connectors
*ConnectorApi* | [**findConnectorById**](Apis/ConnectorApi.md#findconnectorbyid) | **GET** /connectors/{connector_id} | Get the details of a connector
*ConnectorApi* | [**importConnector**](Apis/ConnectorApi.md#importconnector) | **POST** /connectors/import | Import existing connector
*ConnectorApi* | [**registerConnector**](Apis/ConnectorApi.md#registerconnector) | **POST** /connectors | Register a new connector
*ConnectorApi* | [**unregisterConnector**](Apis/ConnectorApi.md#unregisterconnector) | **DELETE** /connectors/{connector_id} | Unregister a connector
*DatasetApi* | [**addOrReplaceDatasetCompatibilityElements**](Apis/DatasetApi.md#addorreplacedatasetcompatibilityelements) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Add Dataset Compatibility elements.
*DatasetApi* | [**copyDataset**](Apis/DatasetApi.md#copydataset) | **POST** /organizations/{organization_id}/datasets/copy | Copy a Dataset to another Dataset. Source must have a read capable connector and Target a write capable connector.
*DatasetApi* | [**createDataset**](Apis/DatasetApi.md#createdataset) | **POST** /organizations/{organization_id}/datasets | Create a new Dataset
*DatasetApi* | [**createSubDataset**](Apis/DatasetApi.md#createsubdataset) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/subdataset | Run a query on a dataset
*DatasetApi* | [**deleteDataset**](Apis/DatasetApi.md#deletedataset) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id} | Delete a dataset
*DatasetApi* | [**findAllDatasets**](Apis/DatasetApi.md#findalldatasets) | **GET** /organizations/{organization_id}/datasets | List all Datasets
*DatasetApi* | [**findDatasetById**](Apis/DatasetApi.md#finddatasetbyid) | **GET** /organizations/{organization_id}/datasets/{dataset_id} | Get the details of a Dataset
*DatasetApi* | [**getDatasetTwingraphStatus**](Apis/DatasetApi.md#getdatasettwingraphstatus) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/job/{job_id}/status | Get the status of twingraph import
*DatasetApi* | [**importDataset**](Apis/DatasetApi.md#importdataset) | **POST** /organizations/{organization_id}/datasets/import | Import a new Dataset
*DatasetApi* | [**refreshDataset**](Apis/DatasetApi.md#refreshdataset) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/refresh | Refresh dataset
*DatasetApi* | [**removeAllDatasetCompatibilityElements**](Apis/DatasetApi.md#removealldatasetcompatibilityelements) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Remove all Dataset Compatibility elements from the Dataset specified
*DatasetApi* | [**searchDatasets**](Apis/DatasetApi.md#searchdatasets) | **POST** /organizations/{organization_id}/datasets/search | Search Datasets
*DatasetApi* | [**twingraphQuery**](Apis/DatasetApi.md#twingraphquery) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/twingraph | Run a query on a graph instance and return the result as a json
*DatasetApi* | [**updateDataset**](Apis/DatasetApi.md#updatedataset) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id} | Update a dataset
*DatasetApi* | [**uploadTwingraph**](Apis/DatasetApi.md#uploadtwingraph) | **POST** /organizations/{organization_id}/datasets/{dataset_id} | Upload Twingraph with ZIP File
*OrganizationApi* | [**addOrganizationAccessControl**](Apis/OrganizationApi.md#addorganizationaccesscontrol) | **POST** /organizations/{organization_id}/security/access | Add a control access to the Organization
*OrganizationApi* | [**findAllOrganizations**](Apis/OrganizationApi.md#findallorganizations) | **GET** /organizations | List all Organizations
*OrganizationApi* | [**findOrganizationById**](Apis/OrganizationApi.md#findorganizationbyid) | **GET** /organizations/{organization_id} | Get the details of an Organization
*OrganizationApi* | [**getAllPermissions**](Apis/OrganizationApi.md#getallpermissions) | **GET** /organizations/permissions | Get all permissions per components
*OrganizationApi* | [**getOrganizationAccessControl**](Apis/OrganizationApi.md#getorganizationaccesscontrol) | **GET** /organizations/{organization_id}/security/access/{identity_id} | Get a control access for the Organization
*OrganizationApi* | [**getOrganizationPermissions**](Apis/OrganizationApi.md#getorganizationpermissions) | **GET** /organizations/{organization_id}/permissions/{role} | Get the Organization permissions by given role
*OrganizationApi* | [**getOrganizationSecurity**](Apis/OrganizationApi.md#getorganizationsecurity) | **GET** /organizations/{organization_id}/security | Get the Organization security information
*OrganizationApi* | [**getOrganizationSecurityUsers**](Apis/OrganizationApi.md#getorganizationsecurityusers) | **GET** /organizations/{organization_id}/security/users | Get the Organization security users list
*OrganizationApi* | [**registerOrganization**](Apis/OrganizationApi.md#registerorganization) | **POST** /organizations | Register a new organization
*OrganizationApi* | [**removeOrganizationAccessControl**](Apis/OrganizationApi.md#removeorganizationaccesscontrol) | **DELETE** /organizations/{organization_id}/security/access/{identity_id} | Remove the specified access from the given Organization
*OrganizationApi* | [**setOrganizationDefaultSecurity**](Apis/OrganizationApi.md#setorganizationdefaultsecurity) | **POST** /organizations/{organization_id}/security/default | Set the Organization default security
*OrganizationApi* | [**unregisterOrganization**](Apis/OrganizationApi.md#unregisterorganization) | **DELETE** /organizations/{organization_id} | Unregister an organization
*OrganizationApi* | [**updateOrganization**](Apis/OrganizationApi.md#updateorganization) | **PATCH** /organizations/{organization_id} | Update an Organization
*OrganizationApi* | [**updateOrganizationAccessControl**](Apis/OrganizationApi.md#updateorganizationaccesscontrol) | **PATCH** /organizations/{organization_id}/security/access/{identity_id} | Update the specified access to User for an Organization
*OrganizationApi* | [**updateSolutionsContainerRegistryByOrganizationId**](Apis/OrganizationApi.md#updatesolutionscontainerregistrybyorganizationid) | **PATCH** /organizations/{organization_id}/services/solutionsContainerRegistry | Update the solutions container registry configuration for the Organization specified
*OrganizationApi* | [**updateStorageByOrganizationId**](Apis/OrganizationApi.md#updatestoragebyorganizationid) | **PATCH** /organizations/{organization_id}/services/storage | Update storage configuration for the Organization specified
*OrganizationApi* | [**updateTenantCredentialsByOrganizationId**](Apis/OrganizationApi.md#updatetenantcredentialsbyorganizationid) | **PATCH** /organizations/{organization_id}/services/tenantCredentials | Update tenant credentials for the Organization specified
*ScenarioApi* | [**addOrReplaceScenarioParameterValues**](Apis/ScenarioApi.md#addorreplacescenarioparametervalues) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/parameterValues | Add (or replace) Parameter Values for the Scenario specified
*ScenarioApi* | [**addScenarioAccessControl**](Apis/ScenarioApi.md#addscenarioaccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access | Add a control access to the Scenario
*ScenarioApi* | [**compareScenarios**](Apis/ScenarioApi.md#comparescenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/compare/{compared_scenario_id} | Compare the Scenario with another one and returns the difference for parameters values
*ScenarioApi* | [**createScenario**](Apis/ScenarioApi.md#createscenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Create a new Scenario
*ScenarioApi* | [**deleteAllScenarios**](Apis/ScenarioApi.md#deleteallscenarios) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Delete all Scenarios of the Workspace
*ScenarioApi* | [**deleteScenario**](Apis/ScenarioApi.md#deletescenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Delete a scenario
*ScenarioApi* | [**downloadScenarioData**](Apis/ScenarioApi.md#downloadscenariodata) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/downloads | Download Scenario data
*ScenarioApi* | [**findAllScenarios**](Apis/ScenarioApi.md#findallscenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | List all Scenarios
*ScenarioApi* | [**findAllScenariosByValidationStatus**](Apis/ScenarioApi.md#findallscenariosbyvalidationstatus) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/{validationStatus} | List all Scenarios by validation status
*ScenarioApi* | [**findScenarioById**](Apis/ScenarioApi.md#findscenariobyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Get the details of an scenario
*ScenarioApi* | [**getScenarioAccessControl**](Apis/ScenarioApi.md#getscenarioaccesscontrol) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access/{identity_id} | Get a control access for the Scenario
*ScenarioApi* | [**getScenarioDataDownloadJobInfo**](Apis/ScenarioApi.md#getscenariodatadownloadjobinfo) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/downloads/{download_id} | Get Scenario data download URL
*ScenarioApi* | [**getScenarioPermissions**](Apis/ScenarioApi.md#getscenariopermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/permissions/{role} | Get the Scenario permission by given role
*ScenarioApi* | [**getScenarioSecurity**](Apis/ScenarioApi.md#getscenariosecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security | Get the Scenario security information
*ScenarioApi* | [**getScenarioSecurityUsers**](Apis/ScenarioApi.md#getscenariosecurityusers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/users | Get the Scenario security users list
*ScenarioApi* | [**getScenarioValidationStatusById**](Apis/ScenarioApi.md#getscenariovalidationstatusbyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/ValidationStatus | Get the validation status of an scenario
*ScenarioApi* | [**getScenariosTree**](Apis/ScenarioApi.md#getscenariostree) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/tree | Get the Scenarios Tree
*ScenarioApi* | [**removeAllScenarioParameterValues**](Apis/ScenarioApi.md#removeallscenarioparametervalues) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/parameterValues | Remove all Parameter Values from the Scenario specified
*ScenarioApi* | [**removeScenarioAccessControl**](Apis/ScenarioApi.md#removescenarioaccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access/{identity_id} | Remove the specified access from the given Organization Scenario
*ScenarioApi* | [**setScenarioDefaultSecurity**](Apis/ScenarioApi.md#setscenariodefaultsecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/default | Set the Scenario default security
*ScenarioApi* | [**updateScenario**](Apis/ScenarioApi.md#updatescenario) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Update a scenario
*ScenarioApi* | [**updateScenarioAccessControl**](Apis/ScenarioApi.md#updatescenarioaccesscontrol) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access/{identity_id} | Update the specified access to User for a Scenario
*ScenariorunApi* | [**deleteHistoricalDataOrganization**](Apis/ScenariorunApi.md#deletehistoricaldataorganization) | **DELETE** /organizations/{organization_id}/scenarioruns/historicaldata | Delete all historical ScenarioRuns in the Organization
*ScenariorunApi* | [**deleteHistoricalDataScenario**](Apis/ScenariorunApi.md#deletehistoricaldatascenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns/historicaldata | Delete all historical ScenarioRuns in the Scenario
*ScenariorunApi* | [**deleteHistoricalDataWorkspace**](Apis/ScenariorunApi.md#deletehistoricaldataworkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarioruns/historicaldata | Delete all historical ScenarioRuns in the Workspace
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
*ScenariorunresultApi* | [**getScenarioRunResult**](Apis/ScenariorunresultApi.md#getscenariorunresult) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns/{scenariorun_id}/probes/{probe_id} | Get a ScenarioRunResult in the Organization
*ScenariorunresultApi* | [**sendScenarioRunResult**](Apis/ScenariorunresultApi.md#sendscenariorunresult) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns/{scenariorun_id}/probes/{probe_id} | Create a new ScenarioRunResult in the Organization
*SolutionApi* | [**addOrReplaceParameterGroups**](Apis/SolutionApi.md#addorreplaceparametergroups) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Add Parameter Groups. Any item with the same ID will be overwritten
*SolutionApi* | [**addOrReplaceParameters**](Apis/SolutionApi.md#addorreplaceparameters) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameters | Add Parameters. Any item with the same ID will be overwritten
*SolutionApi* | [**addOrReplaceRunTemplates**](Apis/SolutionApi.md#addorreplaceruntemplates) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Add Run Templates. Any item with the same ID will be overwritten
*SolutionApi* | [**addSolutionAccessControl**](Apis/SolutionApi.md#addsolutionaccesscontrol) | **POST** /organizations/{organization_id}/solutions/{solution_id}/security/access | Add a control access to the Solution
*SolutionApi* | [**createSolution**](Apis/SolutionApi.md#createsolution) | **POST** /organizations/{organization_id}/solutions | Register a new solution
*SolutionApi* | [**deleteSolution**](Apis/SolutionApi.md#deletesolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution
*SolutionApi* | [**deleteSolutionRunTemplate**](Apis/SolutionApi.md#deletesolutionruntemplate) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Remove the specified Solution Run Template
*SolutionApi* | [**downloadRunTemplateHandler**](Apis/SolutionApi.md#downloadruntemplatehandler) | **GET** /organizations/{organization_id}/solutions/{solution_id}/runtemplates/{run_template_id}/handlers/{handler_id}/download | Download a Run Template step handler zip file
*SolutionApi* | [**findAllSolutions**](Apis/SolutionApi.md#findallsolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions
*SolutionApi* | [**findSolutionById**](Apis/SolutionApi.md#findsolutionbyid) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution
*SolutionApi* | [**getSolutionAccessControl**](Apis/SolutionApi.md#getsolutionaccesscontrol) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Get a control access for the Solution
*SolutionApi* | [**getSolutionSecurityUsers**](Apis/SolutionApi.md#getsolutionsecurityusers) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/users | Get the Solution security users list
*SolutionApi* | [**removeAllRunTemplates**](Apis/SolutionApi.md#removeallruntemplates) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Remove all Run Templates from the Solution specified
*SolutionApi* | [**removeAllSolutionParameterGroups**](Apis/SolutionApi.md#removeallsolutionparametergroups) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Remove all Parameter Groups from the Solution specified
*SolutionApi* | [**removeAllSolutionParameters**](Apis/SolutionApi.md#removeallsolutionparameters) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameters | Remove all Parameters from the Solution specified
*SolutionApi* | [**removeSolutionAccessControl**](Apis/SolutionApi.md#removesolutionaccesscontrol) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Remove the specified access from the given Organization Solution
*SolutionApi* | [**updateSolution**](Apis/SolutionApi.md#updatesolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution
*SolutionApi* | [**updateSolutionAccessControl**](Apis/SolutionApi.md#updatesolutionaccesscontrol) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Update the specified access to User for a Solution
*SolutionApi* | [**updateSolutionRunTemplate**](Apis/SolutionApi.md#updatesolutionruntemplate) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Update the specified Solution Run Template
*SolutionApi* | [**uploadRunTemplateHandler**](Apis/SolutionApi.md#uploadruntemplatehandler) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runtemplates/{run_template_id}/handlers/{handler_id}/upload | Upload a Run Template step handler zip file
*TwingraphApi* | [**batchQuery**](Apis/TwingraphApi.md#batchquery) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/batch-query | Run a query on a graph instance and return the result as a zip file in async mode
*TwingraphApi* | [**batchUploadUpdate**](Apis/TwingraphApi.md#batchuploadupdate) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/batch | Async batch update by loading a CSV file on a graph instance 
*TwingraphApi* | [**createEntities**](Apis/TwingraphApi.md#createentities) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Create new entities in a graph instance
*TwingraphApi* | [**createGraph**](Apis/TwingraphApi.md#creategraph) | **POST** /organizations/{organization_id}/twingraph/{graph_id} | Create a new graph
*TwingraphApi* | [**delete**](Apis/TwingraphApi.md#delete) | **DELETE** /organizations/{organization_id}/twingraph/{graph_id} | Delete all versions of a graph and his metadatas
*TwingraphApi* | [**deleteEntities**](Apis/TwingraphApi.md#deleteentities) | **DELETE** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Delete entities in a graph instance
*TwingraphApi* | [**downloadGraph**](Apis/TwingraphApi.md#downloadgraph) | **GET** /organizations/{organization_id}/twingraph/download/{hash} | Download a graph compressed in a zip file
*TwingraphApi* | [**findAllTwingraphs**](Apis/TwingraphApi.md#findalltwingraphs) | **GET** /organizations/{organization_id}/twingraphs | Return the list of all graphs stored in the organization
*TwingraphApi* | [**getEntities**](Apis/TwingraphApi.md#getentities) | **GET** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Get entities in a graph instance
*TwingraphApi* | [**getGraphMetaData**](Apis/TwingraphApi.md#getgraphmetadata) | **GET** /organizations/{organization_id}/twingraph/{graph_id}/metadata | Return the metaData of the specified graph
*TwingraphApi* | [**importGraph**](Apis/TwingraphApi.md#importgraph) | **POST** /organizations/{organization_id}/twingraph/import | Import a new version of a twin graph
*TwingraphApi* | [**jobStatus**](Apis/TwingraphApi.md#jobstatus) | **GET** /organizations/{organization_id}/job/{job_id}/status | Get the status of a job
*TwingraphApi* | [**query**](Apis/TwingraphApi.md#query) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/query | Run a query on a graph instance
*TwingraphApi* | [**updateEntities**](Apis/TwingraphApi.md#updateentities) | **PATCH** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Update entities in a graph instance
*TwingraphApi* | [**updateGraphMetaData**](Apis/TwingraphApi.md#updategraphmetadata) | **PATCH** /organizations/{organization_id}/twingraph/{graph_id}/metadata | Update the metaData of the specified graph
*ValidatorApi* | [**createValidator**](Apis/ValidatorApi.md#createvalidator) | **POST** /organizations/{organization_id}/datasets/validators | Register a new validator
*ValidatorApi* | [**createValidatorRun**](Apis/ValidatorApi.md#createvalidatorrun) | **POST** /organizations/{organization_id}/datasets/validators/{validator_id}/history | Register a new validator run
*ValidatorApi* | [**deleteValidator**](Apis/ValidatorApi.md#deletevalidator) | **DELETE** /organizations/{organization_id}/datasets/validators/{validator_id} | Delete a validator
*ValidatorApi* | [**deleteValidatorRun**](Apis/ValidatorApi.md#deletevalidatorrun) | **DELETE** /organizations/{organization_id}/datasets/validators/{validator_id}/history/{validatorrun_id} | Delete a validator run
*ValidatorApi* | [**findAllValidatorRuns**](Apis/ValidatorApi.md#findallvalidatorruns) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id}/history | List all Validator Runs
*ValidatorApi* | [**findAllValidators**](Apis/ValidatorApi.md#findallvalidators) | **GET** /organizations/{organization_id}/datasets/validators | List all Validators
*ValidatorApi* | [**findValidatorById**](Apis/ValidatorApi.md#findvalidatorbyid) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id} | Get the details of a validator
*ValidatorApi* | [**findValidatorRunById**](Apis/ValidatorApi.md#findvalidatorrunbyid) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id}/history/{validatorrun_id} | Get the details of a validator run
*ValidatorApi* | [**runValidator**](Apis/ValidatorApi.md#runvalidator) | **POST** /organizations/{organization_id}/datasets/validators/{validator_id}/run | Run a Validator
*WorkspaceApi* | [**addWorkspaceAccessControl**](Apis/WorkspaceApi.md#addworkspaceaccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/access | Add a control access to the Workspace
*WorkspaceApi* | [**createSecret**](Apis/WorkspaceApi.md#createsecret) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/secret | Create a secret for the Workspace
*WorkspaceApi* | [**createWorkspace**](Apis/WorkspaceApi.md#createworkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace
*WorkspaceApi* | [**deleteAllWorkspaceFiles**](Apis/WorkspaceApi.md#deleteallworkspacefiles) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files | Delete all Workspace files
*WorkspaceApi* | [**deleteWorkspace**](Apis/WorkspaceApi.md#deleteworkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace
*WorkspaceApi* | [**deleteWorkspaceFile**](Apis/WorkspaceApi.md#deleteworkspacefile) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files/delete | Delete a workspace file
*WorkspaceApi* | [**downloadWorkspaceFile**](Apis/WorkspaceApi.md#downloadworkspacefile) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files/download | Download the Workspace File specified
*WorkspaceApi* | [**findAllWorkspaceFiles**](Apis/WorkspaceApi.md#findallworkspacefiles) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files | List all Workspace files
*WorkspaceApi* | [**findAllWorkspaces**](Apis/WorkspaceApi.md#findallworkspaces) | **GET** /organizations/{organization_id}/workspaces | List all Workspaces
*WorkspaceApi* | [**findWorkspaceById**](Apis/WorkspaceApi.md#findworkspacebyid) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of an workspace
*WorkspaceApi* | [**getWorkspaceAccessControl**](Apis/WorkspaceApi.md#getworkspaceaccesscontrol) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Get a control access for the Workspace
*WorkspaceApi* | [**getWorkspacePermissions**](Apis/WorkspaceApi.md#getworkspacepermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/permissions/{role} | Get the Workspace permission by given role
*WorkspaceApi* | [**getWorkspaceSecurity**](Apis/WorkspaceApi.md#getworkspacesecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security | Get the Workspace security information
*WorkspaceApi* | [**getWorkspaceSecurityUsers**](Apis/WorkspaceApi.md#getworkspacesecurityusers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/users | Get the Workspace security users list
*WorkspaceApi* | [**removeWorkspaceAccessControl**](Apis/WorkspaceApi.md#removeworkspaceaccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Remove the specified access from the given Organization Workspace
*WorkspaceApi* | [**setWorkspaceDefaultSecurity**](Apis/WorkspaceApi.md#setworkspacedefaultsecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/default | Set the Workspace default security
*WorkspaceApi* | [**updateWorkspace**](Apis/WorkspaceApi.md#updateworkspace) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id} | Update a workspace
*WorkspaceApi* | [**updateWorkspaceAccessControl**](Apis/WorkspaceApi.md#updateworkspaceaccesscontrol) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Update the specified access to User for a Workspace
*WorkspaceApi* | [**uploadWorkspaceFile**](Apis/WorkspaceApi.md#uploadworkspacefile) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/files | Upload a file for the Workspace


<a name="documentation-for-models"></a>
## Documentation for Models

 - [ComponentRolePermissions](./Models/ComponentRolePermissions.md)
 - [Connector](./Models/Connector.md)
 - [ConnectorParameter](./Models/ConnectorParameter.md)
 - [ConnectorParameterGroup](./Models/ConnectorParameterGroup.md)
 - [ContainerResourceSizeInfo](./Models/ContainerResourceSizeInfo.md)
 - [ContainerResourceSizing](./Models/ContainerResourceSizing.md)
 - [Dataset](./Models/Dataset.md)
 - [DatasetCompatibility](./Models/DatasetCompatibility.md)
 - [DatasetCopyParameters](./Models/DatasetCopyParameters.md)
 - [DatasetSearch](./Models/DatasetSearch.md)
 - [DatasetSourceType](./Models/DatasetSourceType.md)
 - [DatasetTwinGraphInfo](./Models/DatasetTwinGraphInfo.md)
 - [DatasetTwinGraphQuery](./Models/DatasetTwinGraphQuery.md)
 - [Dataset_connector](./Models/Dataset_connector.md)
 - [DeleteHistoricalData](./Models/DeleteHistoricalData.md)
 - [GraphProperties](./Models/GraphProperties.md)
 - [Organization](./Models/Organization.md)
 - [OrganizationAccessControl](./Models/OrganizationAccessControl.md)
 - [OrganizationRole](./Models/OrganizationRole.md)
 - [OrganizationSecurity](./Models/OrganizationSecurity.md)
 - [OrganizationService](./Models/OrganizationService.md)
 - [OrganizationServices](./Models/OrganizationServices.md)
 - [ResourceSizeInfo](./Models/ResourceSizeInfo.md)
 - [RunTemplate](./Models/RunTemplate.md)
 - [RunTemplateHandlerId](./Models/RunTemplateHandlerId.md)
 - [RunTemplateOrchestrator](./Models/RunTemplateOrchestrator.md)
 - [RunTemplateParameter](./Models/RunTemplateParameter.md)
 - [RunTemplateParameterGroup](./Models/RunTemplateParameterGroup.md)
 - [RunTemplateParameterValue](./Models/RunTemplateParameterValue.md)
 - [RunTemplateResourceSizing](./Models/RunTemplateResourceSizing.md)
 - [RunTemplateStepSource](./Models/RunTemplateStepSource.md)
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
 - [ScenarioRunResult](./Models/ScenarioRunResult.md)
 - [ScenarioRunSearch](./Models/ScenarioRunSearch.md)
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
 - [TwinGraphImport](./Models/TwinGraphImport.md)
 - [TwinGraphImportInfo](./Models/TwinGraphImportInfo.md)
 - [TwinGraphQuery](./Models/TwinGraphQuery.md)
 - [Validator](./Models/Validator.md)
 - [ValidatorRun](./Models/ValidatorRun.md)
 - [Workspace](./Models/Workspace.md)
 - [WorkspaceAccessControl](./Models/WorkspaceAccessControl.md)
 - [WorkspaceFile](./Models/WorkspaceFile.md)
 - [WorkspaceRole](./Models/WorkspaceRole.md)
 - [WorkspaceSecret](./Models/WorkspaceSecret.md)
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

