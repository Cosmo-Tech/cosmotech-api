# Scenario
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | [**String**](string.md) | the Scenario unique identifier | [optional] [default to null]
**name** | [**String**](string.md) | the Scenario name | [default to null]
**description** | [**String**](string.md) | the Scenario description | [optional] [default to null]
**tags** | [**List**](string.md) | the list of tags | [optional] [default to null]
**parentId** | [**String**](string.md) | the Scenario parent id | [optional] [default to null]
**ownerId** | [**String**](string.md) | the user id which own this Scenario | [optional] [default to null]
**simulatorId** | [**String**](string.md) | the Simulator Id associated with this Scenario | [optional] [default to null]
**users** | [**List**](ScenarioUser.md) | the list of users Id with their role | [optional] [default to null]
**simulatorName** | [**String**](string.md) |  | [optional] [default to null]
**simulatorAnalysisName** | [**String**](string.md) |  | [optional] [default to null]
**analysis** | [**ScenarioAnalysis**](ScenarioAnalysis.md) |  | [optional] [default to null]
**sendInputToDataWarehouse** | [**Boolean**](boolean.md) | default setting for all Analysis to set whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to Simulation Run | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

