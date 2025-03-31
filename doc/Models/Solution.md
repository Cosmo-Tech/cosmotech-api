# Solution
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | The Solution version unique identifier | [default to null] |
| **organizationId** | **String** | The Organization unique identifier | [default to null] |
| **key** | **String** | The Solution key which groups Solution versions | [default to null] |
| **name** | **String** | The Solution name | [default to null] |
| **description** | **String** | The Solution description | [optional] [default to null] |
| **repository** | **String** | The registry repository containing the image | [default to null] |
| **alwaysPull** | **Boolean** | Set to true if the runtemplate wants to always pull the image | [optional] [default to false] |
| **version** | **String** | The Solution version MAJOR.MINOR.PATCH. Must be aligned with an existing repository tag | [default to null] |
| **ownerId** | **String** | The User id which owns this Solution | [default to null] |
| **sdkVersion** | **String** | The full SDK version used to build this solution, if available | [optional] [default to null] |
| **url** | **String** | An optional URL link to solution page | [optional] [default to null] |
| **tags** | **List** | The list of tags | [optional] [default to null] |
| **parameters** | [**List**](RunTemplateParameter.md) | The list of Run Template Parameters | [default to null] |
| **parameterGroups** | [**List**](RunTemplateParameterGroup.md) | The list of parameters groups for the Run Templates | [default to null] |
| **runTemplates** | [**List**](RunTemplate.md) | List of Run Templates | [default to []] |
| **security** | [**SolutionSecurity**](SolutionSecurity.md) |  | [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

