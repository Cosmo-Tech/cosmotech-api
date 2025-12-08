# RunnerUpdateRequest
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **name** | **String** | the Runner name | [optional] [default to null] |
| **description** | **String** | the Runner description | [optional] [default to null] |
| **tags** | **List** | the list of tags | [optional] [default to null] |
| **runTemplateId** | **String** | the Solution Run Template Id associated with this Runner | [optional] [default to null] |
| **datasetList** | **List** | the list of Dataset Id associated to this Runner Run Template | [optional] [default to null] |
| **runSizing** | [**RunnerResourceSizing**](RunnerResourceSizing.md) | definition of resources needed for the runner run | [optional] [default to null] |
| **parametersValues** | [**List**](RunnerRunTemplateParameterValue.md) | the list of Solution Run Template parameters values | [optional] [default to null] |
| **additionalData** | [**Map**](AnyType.md) | Free form additional data | [optional] [default to null] |
| **solutionName** | **String** | the Solution name | [optional] [default to null] |
| **runTemplateName** | **String** | the Solution Run Template name associated with this Runner | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

