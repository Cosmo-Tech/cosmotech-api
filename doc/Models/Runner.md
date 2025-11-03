# Runner
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | the Runner unique identifier | [default to null] |
| **name** | **String** | the Runner name | [default to null] |
| **description** | **String** | the Runner description | [optional] [default to null] |
| **tags** | **List** | the list of tags | [optional] [default to null] |
| **parentId** | **String** | the Runner parent id | [optional] [default to null] |
| **createInfo** | [**RunnerEditInfo**](RunnerEditInfo.md) | The details of the Runner creation | [default to null] |
| **updateInfo** | [**RunnerEditInfo**](RunnerEditInfo.md) | The details of the Runner last update | [default to null] |
| **rootId** | **String** | the runner root id | [optional] [default to null] |
| **solutionId** | **String** | the Solution Id associated with this Runner | [default to null] |
| **runTemplateId** | **String** | the Solution Run Template Id associated with this Runner | [default to null] |
| **organizationId** | **String** | the associated Organization Id | [default to null] |
| **workspaceId** | **String** | the associated Workspace Id | [default to null] |
| **solutionName** | **String** | the Solution name | [optional] [default to null] |
| **runTemplateName** | **String** | the Solution Run Template name associated with this Runner | [optional] [default to null] |
| **additionalData** | [**Map**](AnyType.md) | Free form additional data | [optional] [default to null] |
| **datasets** | [**RunnerDatasets**](RunnerDatasets.md) |  | [default to null] |
| **runSizing** | [**RunnerResourceSizing**](RunnerResourceSizing.md) |  | [optional] [default to null] |
| **parametersValues** | [**List**](RunnerRunTemplateParameterValue.md) | the list of Solution Run Template parameters values | [default to null] |
| **lastRunInfo** | [**LastRunInfo**](LastRunInfo.md) |  | [default to null] |
| **validationStatus** | [**RunnerValidationStatus**](RunnerValidationStatus.md) |  | [default to null] |
| **security** | [**RunnerSecurity**](RunnerSecurity.md) |  | [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

