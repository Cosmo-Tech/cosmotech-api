# WorkspaceSolution
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **solutionId** | **String** | The Solution Id attached to this workspace | [default to null] |
| **datasetId** | **String** | The Dataset Id attached to this workspace. This dataset will be used to store default values for Solution parameters with file&#39;s varType.  | [optional] [default to null] |
| **defaultParameterValues** | **Map** | A map of parameterId/value to set default values for Solution parameters with simple varType (int, string, ...) | [optional] [default to null] |
| **runTemplateFilter** | **List** | The list of Solution Run Template Id to filter | [optional] [default to null] |
| **defaultRunTemplateDataset** | [**Map**](AnyType.md) | A map of RunTemplateId/DatasetId to set a default dataset for a Run Template | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

