# SolutionCreateRequest
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **key** | **String** | Technical key for resource name convention and version grouping. Must be unique | [default to null] |
| **name** | **String** | Solution name. This name is displayed in the sample webApp | [default to null] |
| **description** | **String** | The Solution description | [optional] [default to null] |
| **repository** | **String** | The registry repository containing the image | [default to null] |
| **version** | **String** | The Solution version MAJOR.MINOR.PATCH | [default to null] |
| **alwaysPull** | **Boolean** | Set to true if the runtemplate wants to always pull the image | [optional] [default to false] |
| **tags** | **List** | The list of tags | [optional] [default to null] |
| **parameters** | [**List**](RunTemplateParameterCreateRequest.md) | The list of Run Template Parameters | [optional] [default to []] |
| **parameterGroups** | [**List**](RunTemplateParameterGroupCreateRequest.md) | The list of parameters groups for the Run Templates | [optional] [default to []] |
| **runTemplates** | [**List**](RunTemplateCreateRequest.md) | List of Run Templates | [optional] [default to []] |
| **url** | **String** | An optional URL link to solution page | [optional] [default to null] |
| **security** | [**SolutionSecurity**](SolutionSecurity.md) |  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

