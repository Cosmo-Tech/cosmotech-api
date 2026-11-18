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
| **datasetList** | **List** | the list of Dataset Id associated to this Runner Run Template | [optional] [default to []] |
| **runSizing** | [**RunnerResourceSizing**](RunnerResourceSizing.md) | Resource requests and limits for this Runner&#39;s runs. If omitted or null, the stored value is null. Runs use the Run Template&#39;s runSizing, or its computeSize preset if the template&#39;s runSizing is also null. Presets set both requests and limits: basic is 3 CPU and 4Gi memory, highcpu is 70 CPU and 130Gi memory, and highmemory is 7 CPU and 57Gi memory.  | [optional] [default to null] |
| **parametersValues** | [**List**](RunnerRunTemplateParameterValue.md) | the list of Solution Run Template parameters values | [optional] [default to []] |
| **additionalData** | [**Map**](AnyType.md) | Free form additional data | [optional] [default to null] |
| **solutionName** | **String** | the Solution name | [optional] [default to null] |
| **runTemplateName** | **String** | the Solution Run Template name associated with this Runner | [optional] [default to null] |
| **security** | [**RunnerSecurity**](RunnerSecurity.md) |  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

