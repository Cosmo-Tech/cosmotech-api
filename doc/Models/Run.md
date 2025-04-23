# Run
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | the Run | [optional] [default to null] |
| **state** | [**RunState**](RunState.md) |  | [optional] [default to null] |
| **organizationId** | **String** | the Organization id | [optional] [default to null] |
| **createInfo** | [**RunEditInfo**](RunEditInfo.md) | The details of the Run creation | [default to null] |
| **workflowId** | **String** | the Cosmo Tech compute cluster Argo Workflow Id to search | [optional] [default to null] |
| **csmSimulationRun** | **String** | the Cosmo Tech Simulation Run Id | [optional] [default to null] |
| **generateName** | **String** | the base name for workflow name generation | [optional] [default to null] |
| **workflowName** | **String** | the Cosmo Tech compute cluster Argo Workflow Name | [optional] [default to null] |
| **workspaceId** | **String** | the Workspace Id | [optional] [default to null] |
| **workspaceKey** | **String** | technical key for resource name convention and version grouping. Must be unique | [optional] [default to null] |
| **runnerId** | **String** | the Runner Id | [optional] [default to null] |
| **solutionId** | **String** | the Solution Id | [optional] [default to null] |
| **runTemplateId** | **String** | the Solution Run Template id | [optional] [default to null] |
| **computeSize** | **String** | the compute size needed for this Analysis. Standard sizes are basic and highcpu. Default is basic | [optional] [default to null] |
| **datasetList** | **List** | the list of Dataset Id associated to this Run | [optional] [default to null] |
| **parametersValues** | [**List**](RunTemplateParameterValue.md) | the list of Run Template parameters values | [optional] [default to null] |
| **nodeLabel** | **String** | the node label request | [optional] [default to null] |
| **containers** | [**List**](RunContainer.md) | the containers list. This information is not returned by the API. | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

