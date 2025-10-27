# WorkspaceCreateRequest
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **key** | **String** | Technical key for resource name convention and version grouping. Must be unique | [default to null] |
| **name** | **String** | Workspace name. This name is displayed in the sample webApp | [default to null] |
| **description** | **String** | The Workspace description | [optional] [default to null] |
| **version** | **String** | The Workspace version MAJOR.MINOR.PATCH. | [optional] [default to null] |
| **tags** | **List** | The list of tags | [optional] [default to null] |
| **solution** | [**WorkspaceSolution**](WorkspaceSolution.md) |  | [default to null] |
| **additionalData** | [**Map**](AnyType.md) | Free form additional data | [optional] [default to null] |
| **datasetCopy** | **Boolean** | Activate the copy of dataset on scenario creation | [optional] [default to true] |
| **security** | [**WorkspaceSecurity**](WorkspaceSecurity.md) |  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

