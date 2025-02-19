# Documentation for Cosmo Tech Platform API

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

| Class | Method | HTTP request | Description |
|------------ | ------------- | ------------- | -------------|
| *ConnectorApi* | [**findAllConnectors**](Apis/ConnectorApi.md#findallconnectors) | **GET** /connectors | List all Connectors |
*ConnectorApi* | [**findConnectorById**](Apis/ConnectorApi.md#findconnectorbyid) | **GET** /connectors/{connector_id} | Get the details of a connector |
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
*DatasetApi* | [**twingraphBatchUpdate**](Apis/DatasetApi.md#twingraphbatchupdate) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/batch | Async batch update by loading a CSV file on a graph instance  |
*DatasetApi* | [**twingraphQuery**](Apis/DatasetApi.md#twingraphquery) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/twingraph | Return the result of a query made on the graph instance as a json |
*DatasetApi* | [**unlinkWorkspace**](Apis/DatasetApi.md#unlinkworkspace) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/unlink |  |
*DatasetApi* | [**updateDataset**](Apis/DatasetApi.md#updatedataset) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id} | Update a dataset |
*DatasetApi* | [**updateDatasetAccessControl**](Apis/DatasetApi.md#updatedatasetaccesscontrol) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id}/security/access/{identity_id} | Update the specified access to User for a Dataset |
*DatasetApi* | [**updateTwingraphEntities**](Apis/DatasetApi.md#updatetwingraphentities) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Update entities in a graph instance |
*DatasetApi* | [**uploadTwingraph**](Apis/DatasetApi.md#uploadtwingraph) | **POST** /organizations/{organization_id}/datasets/{dataset_id} | Upload data from zip file to dataset's twingraph |
| *MetaApi* | [**about**](Apis/MetaApi.md#about) | **GET** /about | Get various information about the API |
| *OrganizationApi* | [**createOrganization**](Apis/OrganizationApi.md#createorganization) | **POST** /organizations | Create a new organization |
*OrganizationApi* | [**createOrganizationAccessControl**](Apis/OrganizationApi.md#createorganizationaccesscontrol) | **POST** /organizations/{organization_id}/security/access | Add a control access to the Organization |
*OrganizationApi* | [**deleteOrganization**](Apis/OrganizationApi.md#deleteorganization) | **DELETE** /organizations/{organization_id} | Delete an organization |
*OrganizationApi* | [**deleteOrganizationAccessControl**](Apis/OrganizationApi.md#deleteorganizationaccesscontrol) | **DELETE** /organizations/{organization_id}/security/access/{identity_id} | Remove the specified access from the given Organization |
*OrganizationApi* | [**getOrganization**](Apis/OrganizationApi.md#getorganization) | **GET** /organizations/{organization_id} | Get the details of an Organization |
*OrganizationApi* | [**getOrganizationAccessControl**](Apis/OrganizationApi.md#getorganizationaccesscontrol) | **GET** /organizations/{organization_id}/security/access/{identity_id} | Get a control access for the Organization |
*OrganizationApi* | [**getOrganizationPermissions**](Apis/OrganizationApi.md#getorganizationpermissions) | **GET** /organizations/{organization_id}/permissions/{role} | Get the Organization permissions by given role |
*OrganizationApi* | [**getOrganizationSecurity**](Apis/OrganizationApi.md#getorganizationsecurity) | **GET** /organizations/{organization_id}/security | Get the Organization security information |
*OrganizationApi* | [**listOrganizationSecurityUsers**](Apis/OrganizationApi.md#listorganizationsecurityusers) | **GET** /organizations/{organization_id}/security/users | Get the Organization security users list |
*OrganizationApi* | [**listOrganizations**](Apis/OrganizationApi.md#listorganizations) | **GET** /organizations | List all Organizations |
*OrganizationApi* | [**listPermissions**](Apis/OrganizationApi.md#listpermissions) | **GET** /organizations/permissions | Get all permissions per components |
*OrganizationApi* | [**updateOrganization**](Apis/OrganizationApi.md#updateorganization) | **PATCH** /organizations/{organization_id} | Update an Organization |
*OrganizationApi* | [**updateOrganizationAccessControl**](Apis/OrganizationApi.md#updateorganizationaccesscontrol) | **PATCH** /organizations/{organization_id}/security/access/{identity_id} | Update the specified access to User for an Organization |
*OrganizationApi* | [**updateOrganizationDefaultSecurity**](Apis/OrganizationApi.md#updateorganizationdefaultsecurity) | **PATCH** /organizations/{organization_id}/security/default | Update the Organization default security |
| *RunApi* | [**deleteRun**](Apis/RunApi.md#deleterun) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id} | Delete a run |
*RunApi* | [**getRun**](Apis/RunApi.md#getrun) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id} | Get the details of a run |
*RunApi* | [**getRunLogs**](Apis/RunApi.md#getrunlogs) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/logs | get the logs for the Run |
*RunApi* | [**getRunStatus**](Apis/RunApi.md#getrunstatus) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/status | get the status for the Run |
*RunApi* | [**listRuns**](Apis/RunApi.md#listruns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs | get the list of Runs for the Runner |
*RunApi* | [**queryRunData**](Apis/RunApi.md#queryrundata) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/data/query | query the run data |
*RunApi* | [**sendRunData**](Apis/RunApi.md#sendrundata) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/data/send | Send data associated to a run |
| *RunnerApi* | [**createRunner**](Apis/RunnerApi.md#createrunner) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners | Create a new Runner |
*RunnerApi* | [**createRunnerAccessControl**](Apis/RunnerApi.md#createrunneraccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access | Add a control access to the Runner |
*RunnerApi* | [**deleteRunner**](Apis/RunnerApi.md#deleterunner) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Delete a runner |
*RunnerApi* | [**deleteRunnerAccessControl**](Apis/RunnerApi.md#deleterunneraccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Remove the specified access from the given Organization Runner |
*RunnerApi* | [**getRunner**](Apis/RunnerApi.md#getrunner) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Get the details of an runner |
*RunnerApi* | [**getRunnerAccessControl**](Apis/RunnerApi.md#getrunneraccesscontrol) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Get a control access for the Runner |
*RunnerApi* | [**getRunnerSecurity**](Apis/RunnerApi.md#getrunnersecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security | Get the Runner security information |
*RunnerApi* | [**listRunnerPermissions**](Apis/RunnerApi.md#listrunnerpermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/permissions/{role} | Get the Runner permission by given role |
*RunnerApi* | [**listRunnerSecurityUsers**](Apis/RunnerApi.md#listrunnersecurityusers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/users | Get the Runner security users list |
*RunnerApi* | [**listRunners**](Apis/RunnerApi.md#listrunners) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners | List all Runners |
*RunnerApi* | [**startRun**](Apis/RunnerApi.md#startrun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/start | Start a run with runner parameters |
*RunnerApi* | [**stopRun**](Apis/RunnerApi.md#stoprun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/stop | Stop the last run |
*RunnerApi* | [**updateRunner**](Apis/RunnerApi.md#updaterunner) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Update a runner |
*RunnerApi* | [**updateRunnerAccessControl**](Apis/RunnerApi.md#updaterunneraccesscontrol) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Update the specified access to User for a Runner |
*RunnerApi* | [**updateRunnerDefaultSecurity**](Apis/RunnerApi.md#updaterunnerdefaultsecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/default | Set the Runner default security |
| *SolutionApi* | [**createSolution**](Apis/SolutionApi.md#createsolution) | **POST** /organizations/{organization_id}/solutions | Create a new solution |
*SolutionApi* | [**createSolutionAccessControl**](Apis/SolutionApi.md#createsolutionaccesscontrol) | **POST** /organizations/{organization_id}/solutions/{solution_id}/security/access | Create solution access control |
*SolutionApi* | [**deleteSolution**](Apis/SolutionApi.md#deletesolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution |
*SolutionApi* | [**deleteSolutionAccessControl**](Apis/SolutionApi.md#deletesolutionaccesscontrol) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Delete solution access control |
*SolutionApi* | [**deleteSolutionParameterGroups**](Apis/SolutionApi.md#deletesolutionparametergroups) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Delete all parameter groups from the solution |
*SolutionApi* | [**deleteSolutionParameters**](Apis/SolutionApi.md#deletesolutionparameters) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameters | Delete all parameters from the solution |
*SolutionApi* | [**deleteSolutionRunTemplate**](Apis/SolutionApi.md#deletesolutionruntemplate) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Delete a specific run template |
*SolutionApi* | [**deleteSolutionRunTemplates**](Apis/SolutionApi.md#deletesolutionruntemplates) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Delete all run templates from the solution |
*SolutionApi* | [**getSolution**](Apis/SolutionApi.md#getsolution) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution |
*SolutionApi* | [**getSolutionAccessControl**](Apis/SolutionApi.md#getsolutionaccesscontrol) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Get solution access control |
*SolutionApi* | [**getSolutionSecurity**](Apis/SolutionApi.md#getsolutionsecurity) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security | Get solution security information |
*SolutionApi* | [**listSolutionSecurityUsers**](Apis/SolutionApi.md#listsolutionsecurityusers) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/users | List solution security users |
*SolutionApi* | [**listSolutions**](Apis/SolutionApi.md#listsolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions |
*SolutionApi* | [**updateSolution**](Apis/SolutionApi.md#updatesolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution |
*SolutionApi* | [**updateSolutionAccessControl**](Apis/SolutionApi.md#updatesolutionaccesscontrol) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Update solution access control |
*SolutionApi* | [**updateSolutionDefaultSecurity**](Apis/SolutionApi.md#updatesolutiondefaultsecurity) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/default | Update solution default security |
*SolutionApi* | [**updateSolutionParameterGroups**](Apis/SolutionApi.md#updatesolutionparametergroups) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Update solution parameter groups |
*SolutionApi* | [**updateSolutionParameters**](Apis/SolutionApi.md#updatesolutionparameters) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/parameters | Update solution parameters |
*SolutionApi* | [**updateSolutionRunTemplate**](Apis/SolutionApi.md#updatesolutionruntemplate) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Update a specific run template |
*SolutionApi* | [**updateSolutionRunTemplates**](Apis/SolutionApi.md#updatesolutionruntemplates) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Update solution run templates |
| *WorkspaceApi* | [**createDatasetLink**](Apis/WorkspaceApi.md#createdatasetlink) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/link |  |
*WorkspaceApi* | [**createWorkspace**](Apis/WorkspaceApi.md#createworkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace |
*WorkspaceApi* | [**createWorkspaceAccessControl**](Apis/WorkspaceApi.md#createworkspaceaccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/access | Add a control access to the Workspace |
*WorkspaceApi* | [**createWorkspaceFile**](Apis/WorkspaceApi.md#createworkspacefile) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/files | Upload a file for the Workspace |
*WorkspaceApi* | [**deleteDatasetLink**](Apis/WorkspaceApi.md#deletedatasetlink) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/link |  |
*WorkspaceApi* | [**deleteWorkspace**](Apis/WorkspaceApi.md#deleteworkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace |
*WorkspaceApi* | [**deleteWorkspaceAccessControl**](Apis/WorkspaceApi.md#deleteworkspaceaccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Remove the specified access from the given Organization Workspace |
*WorkspaceApi* | [**deleteWorkspaceFile**](Apis/WorkspaceApi.md#deleteworkspacefile) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files/delete | Delete a workspace file |
*WorkspaceApi* | [**deleteWorkspaceFiles**](Apis/WorkspaceApi.md#deleteworkspacefiles) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files | Delete all Workspace files |
*WorkspaceApi* | [**getWorkspace**](Apis/WorkspaceApi.md#getworkspace) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of an workspace |
*WorkspaceApi* | [**getWorkspaceAccessControl**](Apis/WorkspaceApi.md#getworkspaceaccesscontrol) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Get a control access for the Workspace |
*WorkspaceApi* | [**getWorkspaceFile**](Apis/WorkspaceApi.md#getworkspacefile) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files/download | Download the Workspace File specified |
*WorkspaceApi* | [**getWorkspaceSecurity**](Apis/WorkspaceApi.md#getworkspacesecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security | Get the Workspace security information |
*WorkspaceApi* | [**listWorkspaceFiles**](Apis/WorkspaceApi.md#listworkspacefiles) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files | List all Workspace files |
*WorkspaceApi* | [**listWorkspaceRolePermissions**](Apis/WorkspaceApi.md#listworkspacerolepermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/permissions/{role} | Get the Workspace permission by given role |
*WorkspaceApi* | [**listWorkspaceSecurityUsers**](Apis/WorkspaceApi.md#listworkspacesecurityusers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/users | Get the Workspace security users list |
*WorkspaceApi* | [**listWorkspaces**](Apis/WorkspaceApi.md#listworkspaces) | **GET** /organizations/{organization_id}/workspaces | List all Workspaces |
*WorkspaceApi* | [**updateWorkspace**](Apis/WorkspaceApi.md#updateworkspace) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id} | Update a workspace |
*WorkspaceApi* | [**updateWorkspaceAccessControl**](Apis/WorkspaceApi.md#updateworkspaceaccesscontrol) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Update the specified access to User for a Workspace |
*WorkspaceApi* | [**updateWorkspaceDefaultSecurity**](Apis/WorkspaceApi.md#updateworkspacedefaultsecurity) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/security/default | Update the Workspace default security |


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AboutInfo](./Models/AboutInfo.md)
 - [AboutInfoVersion](./Models/AboutInfoVersion.md)
 - [ComponentRolePermissions](./Models/ComponentRolePermissions.md)
 - [Connector](./Models/Connector.md)
 - [ConnectorParameter](./Models/ConnectorParameter.md)
 - [ConnectorParameterGroup](./Models/ConnectorParameterGroup.md)
 - [ContainerResourceSizeInfo](./Models/ContainerResourceSizeInfo.md)
 - [ContainerResourceSizing](./Models/ContainerResourceSizing.md)
 - [CreatedRun](./Models/CreatedRun.md)
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
 - [FileUploadMetadata](./Models/FileUploadMetadata.md)
 - [FileUploadValidation](./Models/FileUploadValidation.md)
 - [GraphProperties](./Models/GraphProperties.md)
 - [IngestionStatusEnum](./Models/IngestionStatusEnum.md)
 - [Organization](./Models/Organization.md)
 - [OrganizationAccessControl](./Models/OrganizationAccessControl.md)
 - [OrganizationCreateRequest](./Models/OrganizationCreateRequest.md)
 - [OrganizationRole](./Models/OrganizationRole.md)
 - [OrganizationSecurity](./Models/OrganizationSecurity.md)
 - [OrganizationUpdateRequest](./Models/OrganizationUpdateRequest.md)
 - [QueryResult](./Models/QueryResult.md)
 - [ResourceSizeInfo](./Models/ResourceSizeInfo.md)
 - [Run](./Models/Run.md)
 - [RunContainer](./Models/RunContainer.md)
 - [RunData](./Models/RunData.md)
 - [RunDataQuery](./Models/RunDataQuery.md)
 - [RunLogs](./Models/RunLogs.md)
 - [RunLogsEntry](./Models/RunLogsEntry.md)
 - [RunResourceRequested](./Models/RunResourceRequested.md)
 - [RunState](./Models/RunState.md)
 - [RunStatus](./Models/RunStatus.md)
 - [RunStatusNode](./Models/RunStatusNode.md)
 - [RunTemplate](./Models/RunTemplate.md)
 - [RunTemplateParameter](./Models/RunTemplateParameter.md)
 - [RunTemplateParameterGroup](./Models/RunTemplateParameterGroup.md)
 - [RunTemplateParameterValue](./Models/RunTemplateParameterValue.md)
 - [RunTemplateResourceSizing](./Models/RunTemplateResourceSizing.md)
 - [Runner](./Models/Runner.md)
 - [RunnerAccessControl](./Models/RunnerAccessControl.md)
 - [RunnerCreateRequest](./Models/RunnerCreateRequest.md)
 - [RunnerResourceSizing](./Models/RunnerResourceSizing.md)
 - [RunnerRole](./Models/RunnerRole.md)
 - [RunnerRunTemplateParameterValue](./Models/RunnerRunTemplateParameterValue.md)
 - [RunnerSecurity](./Models/RunnerSecurity.md)
 - [RunnerUpdateRequest](./Models/RunnerUpdateRequest.md)
 - [RunnerValidationStatus](./Models/RunnerValidationStatus.md)
 - [Solution](./Models/Solution.md)
 - [SolutionAccessControl](./Models/SolutionAccessControl.md)
 - [SolutionCreateRequest](./Models/SolutionCreateRequest.md)
 - [SolutionRole](./Models/SolutionRole.md)
 - [SolutionSecurity](./Models/SolutionSecurity.md)
 - [SolutionUpdateRequest](./Models/SolutionUpdateRequest.md)
 - [SourceInfo](./Models/SourceInfo.md)
 - [SubDatasetGraphQuery](./Models/SubDatasetGraphQuery.md)
 - [TwinGraphBatchResult](./Models/TwinGraphBatchResult.md)
 - [TwincacheStatusEnum](./Models/TwincacheStatusEnum.md)
 - [Workspace](./Models/Workspace.md)
 - [WorkspaceAccessControl](./Models/WorkspaceAccessControl.md)
 - [WorkspaceCreateRequest](./Models/WorkspaceCreateRequest.md)
 - [WorkspaceFile](./Models/WorkspaceFile.md)
 - [WorkspaceRole](./Models/WorkspaceRole.md)
 - [WorkspaceSecurity](./Models/WorkspaceSecurity.md)
 - [WorkspaceSolution](./Models/WorkspaceSolution.md)
 - [WorkspaceUpdateRequest](./Models/WorkspaceUpdateRequest.md)
 - [WorkspaceWebApp](./Models/WorkspaceWebApp.md)
 - [ioTypesEnum](./Models/ioTypesEnum.md)
 - [sendRunData_request](./Models/sendRunData_request.md)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

<a name="oAuth2AuthCode"></a>
### oAuth2AuthCode

- **Type**: OAuth
- **Flow**: accessCode
- **Authorization URL**: https://example.com/authorize
- **Scopes**: N/A

