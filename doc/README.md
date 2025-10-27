# Documentation for Cosmo Tech Platform API

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost:8080*

| Class | Method | HTTP request | Description |
|------------ | ------------- | ------------- | -------------|
| *DatasetApi* | [**createDataset**](Apis/DatasetApi.md#createdataset) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets | Create a Dataset |
*DatasetApi* | [**createDatasetAccessControl**](Apis/DatasetApi.md#createdatasetaccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access | Add a control access to the Dataset |
*DatasetApi* | [**createDatasetPart**](Apis/DatasetApi.md#createdatasetpart) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts | Create a data part of a Dataset |
*DatasetApi* | [**deleteDataset**](Apis/DatasetApi.md#deletedataset) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id} | Delete a Dataset |
*DatasetApi* | [**deleteDatasetAccessControl**](Apis/DatasetApi.md#deletedatasetaccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id} | Remove the specified access from the given Dataset |
*DatasetApi* | [**deleteDatasetPart**](Apis/DatasetApi.md#deletedatasetpart) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id} | Delete a Dataset part |
*DatasetApi* | [**downloadDatasetPart**](Apis/DatasetApi.md#downloaddatasetpart) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id}/download | Download data from a dataset part |
*DatasetApi* | [**getDataset**](Apis/DatasetApi.md#getdataset) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id} | Retrieve a Dataset |
*DatasetApi* | [**getDatasetAccessControl**](Apis/DatasetApi.md#getdatasetaccesscontrol) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id} | Get a control access for the Dataset |
*DatasetApi* | [**getDatasetPart**](Apis/DatasetApi.md#getdatasetpart) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id} | Retrieve a data part of a Dataset |
*DatasetApi* | [**listDatasetParts**](Apis/DatasetApi.md#listdatasetparts) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts | Retrieve all dataset parts of a Dataset |
*DatasetApi* | [**listDatasetSecurityUsers**](Apis/DatasetApi.md#listdatasetsecurityusers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/users | Get the Dataset security users list |
*DatasetApi* | [**listDatasets**](Apis/DatasetApi.md#listdatasets) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets | Retrieve a list of defined Dataset |
*DatasetApi* | [**queryData**](Apis/DatasetApi.md#querydata) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id}/query | Query data of a Dataset part. This endpoint is only available for dataset parts that support queries (type == DB).  |
*DatasetApi* | [**replaceDatasetPart**](Apis/DatasetApi.md#replacedatasetpart) | **PUT** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id} | Replace existing dataset parts of a Dataset |
*DatasetApi* | [**searchDatasetParts**](Apis/DatasetApi.md#searchdatasetparts) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/search | Search Dataset parts by tags |
*DatasetApi* | [**searchDatasets**](Apis/DatasetApi.md#searchdatasets) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/search | Search Datasets by tags |
*DatasetApi* | [**updateDataset**](Apis/DatasetApi.md#updatedataset) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id} | Update a Dataset |
*DatasetApi* | [**updateDatasetAccessControl**](Apis/DatasetApi.md#updatedatasetaccesscontrol) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id} | Update the specified access to User for a Dataset |
*DatasetApi* | [**updateDatasetDefaultSecurity**](Apis/DatasetApi.md#updatedatasetdefaultsecurity) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/default | Set the Dataset default security |
*DatasetApi* | [**updateDatasetPart**](Apis/DatasetApi.md#updatedatasetpart) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id} | Update existing dataset parts information of a Dataset |
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
| *RunnerApi* | [**createRunner**](Apis/RunnerApi.md#createrunner) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners | Create a new Runner |
*RunnerApi* | [**createRunnerAccessControl**](Apis/RunnerApi.md#createrunneraccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access | Add a control access to the Runner |
*RunnerApi* | [**deleteRunner**](Apis/RunnerApi.md#deleterunner) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Delete a runner |
*RunnerApi* | [**deleteRunnerAccessControl**](Apis/RunnerApi.md#deleterunneraccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Remove the specified access from the given Runner |
*RunnerApi* | [**getRunner**](Apis/RunnerApi.md#getrunner) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Get the details of a runner |
*RunnerApi* | [**getRunnerAccessControl**](Apis/RunnerApi.md#getrunneraccesscontrol) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Get a control access for the Runner |
*RunnerApi* | [**getRunnerSecurity**](Apis/RunnerApi.md#getrunnersecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security | Get the Runner security information |
*RunnerApi* | [**listRunnerPermissions**](Apis/RunnerApi.md#listrunnerpermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/permissions/{role} | Get the Runner permission by given role |
*RunnerApi* | [**listRunnerSecurityUsers**](Apis/RunnerApi.md#listrunnersecurityusers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/users | Get the Runner security users list |
*RunnerApi* | [**listRunners**](Apis/RunnerApi.md#listrunners) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners | List all Runners |
*RunnerApi* | [**startRun**](Apis/RunnerApi.md#startrun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/start | Start a run with runner parameters |
*RunnerApi* | [**stopRun**](Apis/RunnerApi.md#stoprun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/stop | Stop the last run |
*RunnerApi* | [**updateRunner**](Apis/RunnerApi.md#updaterunner) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Update a runner |
*RunnerApi* | [**updateRunnerAccessControl**](Apis/RunnerApi.md#updaterunneraccesscontrol) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Update the specified access to User for a Runner |
*RunnerApi* | [**updateRunnerDefaultSecurity**](Apis/RunnerApi.md#updaterunnerdefaultsecurity) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/default | Set the Runner default security |
| *SolutionApi* | [**createSolution**](Apis/SolutionApi.md#createsolution) | **POST** /organizations/{organization_id}/solutions | Create a new solution |
*SolutionApi* | [**createSolutionAccessControl**](Apis/SolutionApi.md#createsolutionaccesscontrol) | **POST** /organizations/{organization_id}/solutions/{solution_id}/security/access | Create solution access control |
*SolutionApi* | [**createSolutionParameter**](Apis/SolutionApi.md#createsolutionparameter) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameters | Create solution parameter for a solution |
*SolutionApi* | [**createSolutionParameterGroup**](Apis/SolutionApi.md#createsolutionparametergroup) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Create a solution parameter group |
*SolutionApi* | [**createSolutionRunTemplate**](Apis/SolutionApi.md#createsolutionruntemplate) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Create a solution run template |
*SolutionApi* | [**deleteSolution**](Apis/SolutionApi.md#deletesolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution |
*SolutionApi* | [**deleteSolutionAccessControl**](Apis/SolutionApi.md#deletesolutionaccesscontrol) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Delete solution access control |
*SolutionApi* | [**deleteSolutionParameter**](Apis/SolutionApi.md#deletesolutionparameter) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameters/{parameter_id} | Delete specific parameter from the solution |
*SolutionApi* | [**deleteSolutionParameterGroup**](Apis/SolutionApi.md#deletesolutionparametergroup) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups/{parameter_group_id} | Delete a parameter group from the solution |
*SolutionApi* | [**deleteSolutionRunTemplate**](Apis/SolutionApi.md#deletesolutionruntemplate) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Delete a specific run template |
*SolutionApi* | [**getRunTemplate**](Apis/SolutionApi.md#getruntemplate) | **GET** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Retrieve a solution run templates |
*SolutionApi* | [**getSolution**](Apis/SolutionApi.md#getsolution) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution |
*SolutionApi* | [**getSolutionAccessControl**](Apis/SolutionApi.md#getsolutionaccesscontrol) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Get solution access control |
*SolutionApi* | [**getSolutionParameter**](Apis/SolutionApi.md#getsolutionparameter) | **GET** /organizations/{organization_id}/solutions/{solution_id}/parameters/{parameter_id} | Get the details of a solution parameter |
*SolutionApi* | [**getSolutionParameterGroup**](Apis/SolutionApi.md#getsolutionparametergroup) | **GET** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups/{parameter_group_id} | Get details of a solution parameter group |
*SolutionApi* | [**getSolutionSecurity**](Apis/SolutionApi.md#getsolutionsecurity) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security | Get solution security information |
*SolutionApi* | [**listRunTemplates**](Apis/SolutionApi.md#listruntemplates) | **GET** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | List all solution run templates |
*SolutionApi* | [**listSolutionParameterGroups**](Apis/SolutionApi.md#listsolutionparametergroups) | **GET** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | List all solution parameter groups |
*SolutionApi* | [**listSolutionParameters**](Apis/SolutionApi.md#listsolutionparameters) | **GET** /organizations/{organization_id}/solutions/{solution_id}/parameters | List all solution parameters |
*SolutionApi* | [**listSolutionSecurityUsers**](Apis/SolutionApi.md#listsolutionsecurityusers) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/users | List solution security users |
*SolutionApi* | [**listSolutions**](Apis/SolutionApi.md#listsolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions |
*SolutionApi* | [**updateSolution**](Apis/SolutionApi.md#updatesolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution |
*SolutionApi* | [**updateSolutionAccessControl**](Apis/SolutionApi.md#updatesolutionaccesscontrol) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Update solution access control |
*SolutionApi* | [**updateSolutionDefaultSecurity**](Apis/SolutionApi.md#updatesolutiondefaultsecurity) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/default | Update solution default security |
*SolutionApi* | [**updateSolutionParameter**](Apis/SolutionApi.md#updatesolutionparameter) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/parameters/{parameter_id} | Update solution parameter |
*SolutionApi* | [**updateSolutionParameterGroup**](Apis/SolutionApi.md#updatesolutionparametergroup) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups/{parameter_group_id} | Update a solution parameter group |
*SolutionApi* | [**updateSolutionRunTemplate**](Apis/SolutionApi.md#updatesolutionruntemplate) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Update a specific run template |
| *WorkspaceApi* | [**createWorkspace**](Apis/WorkspaceApi.md#createworkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace |
*WorkspaceApi* | [**createWorkspaceAccessControl**](Apis/WorkspaceApi.md#createworkspaceaccesscontrol) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/access | Add a control access to the Workspace |
*WorkspaceApi* | [**createWorkspaceFile**](Apis/WorkspaceApi.md#createworkspacefile) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/files | Upload a file for the Workspace |
*WorkspaceApi* | [**deleteWorkspace**](Apis/WorkspaceApi.md#deleteworkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace |
*WorkspaceApi* | [**deleteWorkspaceAccessControl**](Apis/WorkspaceApi.md#deleteworkspaceaccesscontrol) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Remove the specified access from the given Workspace |
*WorkspaceApi* | [**deleteWorkspaceFile**](Apis/WorkspaceApi.md#deleteworkspacefile) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files/delete | Delete a workspace file |
*WorkspaceApi* | [**deleteWorkspaceFiles**](Apis/WorkspaceApi.md#deleteworkspacefiles) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files | Delete all Workspace files |
*WorkspaceApi* | [**getWorkspace**](Apis/WorkspaceApi.md#getworkspace) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of a workspace |
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
 - [ContainerResourceSizeInfo](./Models/ContainerResourceSizeInfo.md)
 - [ContainerResourceSizing](./Models/ContainerResourceSizing.md)
 - [CreateInfo](./Models/CreateInfo.md)
 - [CreatedRun](./Models/CreatedRun.md)
 - [Dataset](./Models/Dataset.md)
 - [DatasetAccessControl](./Models/DatasetAccessControl.md)
 - [DatasetCreateRequest](./Models/DatasetCreateRequest.md)
 - [DatasetPart](./Models/DatasetPart.md)
 - [DatasetPartCreateRequest](./Models/DatasetPartCreateRequest.md)
 - [DatasetPartTypeEnum](./Models/DatasetPartTypeEnum.md)
 - [DatasetPartUpdateRequest](./Models/DatasetPartUpdateRequest.md)
 - [DatasetRole](./Models/DatasetRole.md)
 - [DatasetSecurity](./Models/DatasetSecurity.md)
 - [DatasetUpdateRequest](./Models/DatasetUpdateRequest.md)
 - [EditInfo](./Models/EditInfo.md)
 - [LastRunInfo](./Models/LastRunInfo.md)
 - [Organization](./Models/Organization.md)
 - [OrganizationAccessControl](./Models/OrganizationAccessControl.md)
 - [OrganizationCreateRequest](./Models/OrganizationCreateRequest.md)
 - [OrganizationEditInfo](./Models/OrganizationEditInfo.md)
 - [OrganizationRole](./Models/OrganizationRole.md)
 - [OrganizationSecurity](./Models/OrganizationSecurity.md)
 - [OrganizationUpdateRequest](./Models/OrganizationUpdateRequest.md)
 - [ResourceSizeInfo](./Models/ResourceSizeInfo.md)
 - [Run](./Models/Run.md)
 - [RunContainer](./Models/RunContainer.md)
 - [RunEditInfo](./Models/RunEditInfo.md)
 - [RunResourceRequested](./Models/RunResourceRequested.md)
 - [RunState](./Models/RunState.md)
 - [RunStatus](./Models/RunStatus.md)
 - [RunStatusNode](./Models/RunStatusNode.md)
 - [RunTemplate](./Models/RunTemplate.md)
 - [RunTemplateCreateRequest](./Models/RunTemplateCreateRequest.md)
 - [RunTemplateParameter](./Models/RunTemplateParameter.md)
 - [RunTemplateParameterCreateRequest](./Models/RunTemplateParameterCreateRequest.md)
 - [RunTemplateParameterGroup](./Models/RunTemplateParameterGroup.md)
 - [RunTemplateParameterGroupCreateRequest](./Models/RunTemplateParameterGroupCreateRequest.md)
 - [RunTemplateParameterGroupUpdateRequest](./Models/RunTemplateParameterGroupUpdateRequest.md)
 - [RunTemplateParameterUpdateRequest](./Models/RunTemplateParameterUpdateRequest.md)
 - [RunTemplateParameterValue](./Models/RunTemplateParameterValue.md)
 - [RunTemplateResourceSizing](./Models/RunTemplateResourceSizing.md)
 - [RunTemplateUpdateRequest](./Models/RunTemplateUpdateRequest.md)
 - [Runner](./Models/Runner.md)
 - [RunnerAccessControl](./Models/RunnerAccessControl.md)
 - [RunnerCreateRequest](./Models/RunnerCreateRequest.md)
 - [RunnerDatasets](./Models/RunnerDatasets.md)
 - [RunnerEditInfo](./Models/RunnerEditInfo.md)
 - [RunnerResourceSizing](./Models/RunnerResourceSizing.md)
 - [RunnerRole](./Models/RunnerRole.md)
 - [RunnerRunTemplateParameterValue](./Models/RunnerRunTemplateParameterValue.md)
 - [RunnerSecurity](./Models/RunnerSecurity.md)
 - [RunnerUpdateRequest](./Models/RunnerUpdateRequest.md)
 - [RunnerValidationStatus](./Models/RunnerValidationStatus.md)
 - [Solution](./Models/Solution.md)
 - [SolutionAccessControl](./Models/SolutionAccessControl.md)
 - [SolutionCreateRequest](./Models/SolutionCreateRequest.md)
 - [SolutionEditInfo](./Models/SolutionEditInfo.md)
 - [SolutionRole](./Models/SolutionRole.md)
 - [SolutionSecurity](./Models/SolutionSecurity.md)
 - [SolutionUpdateRequest](./Models/SolutionUpdateRequest.md)
 - [Workspace](./Models/Workspace.md)
 - [WorkspaceAccessControl](./Models/WorkspaceAccessControl.md)
 - [WorkspaceCreateRequest](./Models/WorkspaceCreateRequest.md)
 - [WorkspaceEditInfo](./Models/WorkspaceEditInfo.md)
 - [WorkspaceFile](./Models/WorkspaceFile.md)
 - [WorkspaceRole](./Models/WorkspaceRole.md)
 - [WorkspaceSecurity](./Models/WorkspaceSecurity.md)
 - [WorkspaceSolution](./Models/WorkspaceSolution.md)
 - [WorkspaceUpdateRequest](./Models/WorkspaceUpdateRequest.md)
 - [WorkspaceWebApp](./Models/WorkspaceWebApp.md)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

<a name="oAuth2AuthCode"></a>
### oAuth2AuthCode

- **Type**: OAuth
- **Flow**: accessCode
- **Authorization URL**: https://example.com/authorize
- **Scopes**: N/A

