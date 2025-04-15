# SolutionUpdateRequest
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **key** | **String** | Technical key for resource name convention and version grouping. Must be unique | [optional] [default to null] |
| **name** | **String** | The Solution name | [optional] [default to null] |
| **description** | **String** | The Solution description | [optional] [default to null] |
| **repository** | **String** | The registry repository containing the image | [optional] [default to null] |
| **alwaysPull** | **Boolean** | Set to true if the runtemplate wants to always pull the image | [optional] [default to null] |
| **version** | **String** | The Solution version MAJOR.MINOR.PATCH. Must be aligned with an existing repository tag | [optional] [default to null] |
| **url** | **String** | An optional URL link to solution page | [optional] [default to null] |
| **tags** | **List** | The list of tags | [optional] [default to null] |
| **parameters** | [**List**](RunTemplateParameterCreateRequest.md) | The list of Run Template Parameters | [optional] [default to null] |
| **parameterGroups** | [**List**](RunTemplateParameterGroupCreateRequest.md) | The list of parameters groups for the Run Templates | [optional] [default to null] |
| **runTemplates** | [**List**](RunTemplateCreateRequest.md) | List of Run Templates | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

