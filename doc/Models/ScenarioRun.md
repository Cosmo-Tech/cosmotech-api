# ScenarioRun
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | [**String**](string.md) | the ScenarioRun | [optional] [default to null]
**workflowId** | [**String**](string.md) | the Cosmo Tech compute cluster Argo Workflow Id to search | [optional] [default to null]
**workflowName** | [**String**](string.md) | the Cosmo Tech compute cluster Argo Workflow Name | [optional] [default to null]
**ownerId** | [**String**](string.md) | the user id which own this scenariorun | [optional] [default to null]
**workspaceId** | [**String**](string.md) | the Workspace Id | [optional] [default to null]
**workspaceName** | [**String**](string.md) | the Workspace name | [optional] [default to null]
**scenarioId** | [**String**](string.md) | the Scenario Id | [optional] [default to null]
**scenarioName** | [**String**](string.md) | the Scenario name | [optional] [default to null]
**solutionId** | [**String**](string.md) | the Solution Id | [optional] [default to null]
**solutionName** | [**String**](string.md) | the Solution name | [optional] [default to null]
**solutionVersion** | [**String**](string.md) | the Solution version | [optional] [default to null]
**runTemplateId** | [**String**](string.md) | the Solution Run Template id | [optional] [default to null]
**runTemplateName** | [**String**](string.md) | the Run Template name | [optional] [default to null]
**computeSize** | [**String**](string.md) | the compute size needed for this Analysis. Standard sizes are basic and highcpu. Default is basic | [optional] [default to null]
**state** | [**String**](string.md) | the ScenarioRun state | [optional] [default to null]
**failedStep** | [**String**](string.md) | the failed step if state is Failed | [optional] [default to null]
**failedContainerId** | [**String**](string.md) | the failed container Id if state is Failed | [optional] [default to null]
**startTime** | [**String**](string.md) | the ScenarioRun start Date Time | [optional] [default to null]
**endTime** | [**String**](string.md) | the ScenarioRun end Date Time | [optional] [default to null]
**datasetList** | [**List**](string.md) | the list of Dataset Id associated to this Analysis | [optional] [default to null]
**parametersValues** | [**List**](RunTemplateParameterValue.md) | the list of Run Template parameters values | [optional] [default to null]
**sendInputToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to ScenarioRun Run | [optional] [default to null]
**dataWarehouseDB** | [**String**](string.md) | the DataWarehouse database name to send data if sendInputToDataWarehouse is set | [optional] [default to null]
**resultsEventBusResourceUri** | [**String**](string.md) | the event bus which receive Workspace ScenarioRun results messages. Message won&#39;t be send if this is not set | [optional] [default to null]
**scenariorunEventBusResourceUri** | [**String**](string.md) | the event bus which receive Workspace ScenarioRun events messages. Message won&#39;t be send if this is not set | [optional] [default to null]
**nodeLabel** | [**String**](string.md) | the node label request | [optional] [default to null]
**fetchDatasetContainers** | [**List**](ScenarioRunContainer.md) | the containers which fetch the Scenario Datasets | [optional] [default to null]
**fetchScenarioParametersContainer** | [**ScenarioRunContainer**](ScenarioRunContainer.md) |  | [optional] [default to null]
**applyParametersContainer** | [**ScenarioRunContainer**](ScenarioRunContainer.md) |  | [optional] [default to null]
**validateDataContainer** | [**ScenarioRunContainer**](ScenarioRunContainer.md) |  | [optional] [default to null]
**sendDataWarehouseContainer** | [**ScenarioRunContainer**](ScenarioRunContainer.md) |  | [optional] [default to null]
**preRunContainer** | [**ScenarioRunContainer**](ScenarioRunContainer.md) |  | [optional] [default to null]
**runContainer** | [**ScenarioRunContainer**](ScenarioRunContainer.md) |  | [optional] [default to null]
**postRunContainer** | [**ScenarioRunContainer**](ScenarioRunContainer.md) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

