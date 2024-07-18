# Runner
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | the Runner unique identifier | [optional] [default to null] |
| **name** | **String** | the Runner name | [optional] [default to null] |
| **description** | **String** | the Runner description | [optional] [default to null] |
| **tags** | **List** | the list of tags | [optional] [default to null] |
| **parentId** | **String** | the Runner parent id | [optional] [default to null] |
| **ownerId** | **String** | the user id which own this Runner | [optional] [default to null] |
| **rootId** | **String** | the runner root id | [optional] [default to null] |
| **solutionId** | **String** | the Solution Id associated with this Runner | [optional] [default to null] |
| **runTemplateId** | **String** | the Solution Run Template Id associated with this Runner | [optional] [default to null] |
| **organizationId** | **String** | the associated Organization Id | [optional] [default to null] |
| **workspaceId** | **String** | the associated Workspace Id | [optional] [default to null] |
| **creationDate** | **Long** | the Runner creation date | [optional] [default to null] |
| **lastUpdate** | **Long** | the last time a Runner was updated | [optional] [default to null] |
| **ownerName** | **String** | the name of the owner | [optional] [default to null] |
| **solutionName** | **String** | the Solution name | [optional] [default to null] |
| **runTemplateName** | **String** | the Solution Run Template name associated with this Runner | [optional] [default to null] |
| **datasetList** | **List** | the list of Dataset Id associated to this Runner Run Template | [default to []] |
| **runSizing** | [**RunnerResourceSizing**](RunnerResourceSizing.md) |  | [optional] [default to null] |
| **parametersValues** | [**List**](RunnerRunTemplateParameterValue.md) | the list of Solution Run Template parameters values | [optional] [default to null] |
| **lastRunId** | **String** | last run id from current runner | [optional] [default to null] |
| **validationStatus** | [**RunnerValidationStatus**](RunnerValidationStatus.md) |  | [optional] [default to null] |
| **security** | [**RunnerSecurity**](RunnerSecurity.md) |  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

