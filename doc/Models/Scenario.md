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
**solutionId** | [**String**](string.md) | the Solution Id associated with this Scenario | [optional] [default to null]
**runTemplateId** | [**String**](string.md) | the Solution Run Template Id associated with this Scenario | [optional] [default to null]
**users** | [**List**](ScenarioUser.md) | the list of users Id with their role | [optional] [default to null]
**solutionName** | [**String**](string.md) | the Solution name | [optional] [default to null]
**runTemplateName** | [**String**](string.md) | the Solution Run Template name associated with this Scenario | [optional] [default to null]
**datasetList** | [**List**](string.md) | the list of Dataset Id associated to this Scenario Run Template | [optional] [default to null]
**parametersValues** | [**List**](ScenarioRunTemplateParameterValue.md) | the list of Solution Run Template parameters values | [optional] [default to null]
**sendInputToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to Simulation Run | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

