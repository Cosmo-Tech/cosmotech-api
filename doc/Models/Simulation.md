# Simulation
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | [**String**](string.md) | the Simulation | [optional] [default to null]
**jobId** | [**String**](string.md) | the Platform compute cluster Job Id | [optional] [default to null]
**ownerId** | [**String**](string.md) | the user id which own this simulation | [optional] [default to null]
**workspaceId** | [**String**](string.md) | the Workspace Id | [optional] [default to null]
**workspaceName** | [**String**](string.md) | the Workspace name | [optional] [default to null]
**scenarioId** | [**String**](string.md) | the Scenario Id | [optional] [default to null]
**scenarioName** | [**String**](string.md) | the Scenario name | [optional] [default to null]
**simulatorId** | [**String**](string.md) | the Simulator Id | [optional] [default to null]
**simulatorName** | [**String**](string.md) | the Simulator name | [optional] [default to null]
**simulatorVersion** | [**String**](string.md) | the Simulator version | [optional] [default to null]
**simulatorAnalysisId** | [**String**](string.md) | the Simulator Analysis id | [optional] [default to null]
**simulatorAnalysisName** | [**String**](string.md) | the Simulator Analysis name | [optional] [default to null]
**computeSize** | [**String**](string.md) | the compute size needed for this Analysis. Standard sizes are basic and highcpu. Default is basic | [optional] [default to null]
**state** | [**String**](string.md) | the Simulation state | [optional] [default to null]
**startTime** | [**String**](string.md) | the Simulation start Date Time | [optional] [default to null]
**endTime** | [**String**](string.md) | the Simulation end Date Time | [optional] [default to null]
**datasetList** | [**List**](string.md) | the list of Dataset Id associated to this Analysis | [optional] [default to null]
**parametersValues** | [**List**](SimulationAnalysisParameterValue.md) | the list of Simulator Analysis parameters values | [optional] [default to null]
**sendInputToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to Simulation Run | [optional] [default to null]
**dataWarehouseDB** | [**String**](string.md) | the DataWarehouse database name to send data if sendInputToDataWarehouse is set | [optional] [default to null]
**resultsEventBusResourceUri** | [**String**](string.md) | the event bus which receive Workspace Simulation results messages. Message won&#39;t be send if this is not set | [optional] [default to null]
**simulationEventBusResourceUri** | [**String**](string.md) | the event bus which receive Workspace Simulation events messages. Message won&#39;t be send if this is not set | [optional] [default to null]
**nodeLabel** | [**String**](string.md) | the node label request | [optional] [default to null]
**initContainers** | [**List**](SimulationContainers.md) | the list of init containers | [optional] [default to null]
**mainContainer** | [**SimulationContainers**](SimulationContainers.md) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

