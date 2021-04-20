# ScenarioRunAllOf
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**datasetList** | [**List**](string.md) | the list of Dataset Id associated to this Analysis | [optional] [default to null]
**parametersValues** | [**List**](RunTemplateParameterValue.md) | the list of Run Template parameters values | [optional] [default to null]
**sendInputToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to ScenarioRun Run | [optional] [default to null]
**dataWarehouseDB** | [**String**](string.md) | the DataWarehouse database name to send data if sendInputToDataWarehouse is set | [optional] [default to null]
**resultsEventBusResourceUri** | [**String**](string.md) | the event bus which receive Workspace ScenarioRun results messages. Message won&#39;t be send if this is not set | [optional] [default to null]
**scenariorunEventBusResourceUri** | [**String**](string.md) | the event bus which receive Workspace ScenarioRun events messages. Message won&#39;t be send if this is not set | [optional] [default to null]
**nodeLabel** | [**String**](string.md) | the node label request | [optional] [default to null]
**initContainers** | [**List**](ScenarioRunContainers.md) | the list of init containers | [optional] [default to null]
**mainContainer** | [**ScenarioRunContainers**](ScenarioRunContainers.md) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

