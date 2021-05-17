# ScenarioRun
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | [**String**](string.md) | the ScenarioRun | [optional] [default to null]
**organizationId** | [**String**](string.md) | the Organization id | [optional] [default to null]
**workflowId** | [**String**](string.md) | the Cosmo Tech compute cluster Argo Workflow Id to search | [optional] [default to null]
**generateName** | [**String**](string.md) | the base name for workflow name generation | [optional] [default to null]
**workflowName** | [**String**](string.md) | the Cosmo Tech compute cluster Argo Workflow Name | [optional] [default to null]
**ownerId** | [**String**](string.md) | the user id which own this scenariorun | [optional] [default to null]
**workspaceId** | [**String**](string.md) | the Workspace Id | [optional] [default to null]
**workspaceKey** | [**String**](string.md) | technical key for resource name convention and version grouping. Must be unique | [optional] [default to null]
**scenarioId** | [**String**](string.md) | the Scenario Id | [optional] [default to null]
**solutionId** | [**String**](string.md) | the Solution Id | [optional] [default to null]
**runTemplateId** | [**String**](string.md) | the Solution Run Template id | [optional] [default to null]
**computeSize** | [**String**](string.md) | the compute size needed for this Analysis. Standard sizes are basic and highcpu. Default is basic | [optional] [default to null]
**state** | [**String**](string.md) | the ScenarioRun state | [optional] [default to null]
**failedStep** | [**String**](string.md) | the failed step if state is Failed | [optional] [default to null]
**failedContainerId** | [**String**](string.md) | the failed container Id if state is Failed | [optional] [default to null]
**startTime** | [**String**](string.md) | the ScenarioRun start Date Time | [optional] [default to null]
**endTime** | [**String**](string.md) | the ScenarioRun end Date Time | [optional] [default to null]
**datasetList** | [**List**](string.md) | the list of Dataset Id associated to this Analysis | [optional] [default to null]
**parametersValues** | [**List**](RunTemplateParameterValue.md) | the list of Run Template parameters values | [optional] [default to null]
**sendDatasetsToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the Datasets values are send to the DataWarehouse prior to Simulation Run. If not set follow the Workspace setting | [optional] [default to null]
**sendInputParametersToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the input parameters values are send to the DataWarehouse prior to Simulation Run. If not set follow the Workspace setting | [optional] [default to null]
**nodeLabel** | [**String**](string.md) | the node label request | [optional] [default to null]
**containers** | [**List**](ScenarioRunContainer.md) | the containers list | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

