# SimulationStartSimulator
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**simulatorId** | [**String**](string.md) | the Simulator Id | [optional] [default to null]
**simulatorAnalysisId** | [**String**](string.md) | the Simulator Analysis id | [optional] [default to null]
**datasetList** | [**List**](string.md) | the list of Dataset Id associated to this Analysis | [optional] [default to null]
**parametersValues** | [**List**](SimulationAnalysisParameterValue.md) | the list of Simulator Analysis parameters values | [optional] [default to null]
**sendInputToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to Simulation Run | [optional] [default to null]
**dataWarehouseDB** | [**String**](string.md) | the DataWarehouse database name to send data if sendInputToDataWarehouse is set | [optional] [default to null]
**resultsEventBusResourceUri** | [**String**](string.md) | the event bus which receive Workspace Simulation results messages. Message won&#39;t be send if this is not set | [optional] [default to null]
**simulationEventBusResourceUri** | [**String**](string.md) | the event bus which receive Workspace Simulation events messages. Message won&#39;t be send if this is not set | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

