# SolutionUpdateRequest
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **key** | **String** | technical key for resource name convention and version grouping. Must be unique | [optional] [default to null] |
| **name** | **String** | The Solution name | [optional] [default to null] |
| **description** | **String** | The Solution description | [optional] [default to null] |
| **repository** | **String** | The registry repository containing the image | [optional] [default to null] |
| **alwaysPull** | **Boolean** | Set to true if the runtemplate wants to always pull the image | [optional] [default to false] |
| **csmSimulator** | **String** | The main Cosmo Tech simulator name used in standard Run Template | [optional] [default to null] |
| **version** | **String** | The Solution version MAJOR.MINOR.PATCH. Must be aligned with an existing repository tag | [optional] [default to null] |
| **sdkVersion** | **String** | The MAJOR.MINOR version used to build this solution | [optional] [default to null] |
| **url** | **String** | An optional URL link to solution page | [optional] [default to null] |
| **tags** | **List** | The list of tags | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

