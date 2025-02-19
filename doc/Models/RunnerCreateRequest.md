# RunnerCreateRequest
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **name** | **String** | the Runner name | [default to null] |
| **description** | **String** | the Runner description | [optional] [default to null] |
| **tags** | **List** | the list of tags | [optional] [default to null] |
| **solutionId** | **String** | the Solution Id associated with this Runner | [default to null] |
| **parentId** | **String** | the Runner parent id | [optional] [default to null] |
| **runTemplateId** | **String** | the Solution Run Template Id associated with this Runner | [default to null] |
| **datasetList** | **List** | the list of Dataset Id associated to this Runner Run Template | [optional] [default to null] |
| **runSizing** | [**RunnerResourceSizing**](RunnerResourceSizing.md) |  | [optional] [default to null] |
| **parametersValues** | [**List**](RunnerRunTemplateParameterValue.md) | the list of Solution Run Template parameters values | [optional] [default to null] |
| **ownerName** | **String** | the name of the owner | [default to null] |
| **solutionName** | **String** | the Solution name | [optional] [default to null] |
| **runTemplateName** | **String** | the Solution Run Template name associated with this Runner | [optional] [default to null] |
| **security** | [**RunnerSecurity**](RunnerSecurity.md) |  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

