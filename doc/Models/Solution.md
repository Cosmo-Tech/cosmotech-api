# Solution
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | the Solution version unique identifier | [optional] [default to null] |
| **organizationId** | **String** | the Organization unique identifier | [optional] [default to null] |
| **key** | **String** | the Solution key which group Solution versions | [optional] [default to null] |
| **name** | **String** | the Solution name | [optional] [default to null] |
| **description** | **String** | the Solution description | [optional] [default to null] |
| **repository** | **String** | the registry repository containing the image | [optional] [default to null] |
| **alwaysPull** | **Boolean** | set to true if the runtemplate wants to always pull the image | [optional] [default to false] |
| **csmSimulator** | **String** | the main Cosmo Tech simulator name used in standard Run Template | [optional] [default to null] |
| **version** | **String** | the Solution version MAJOR.MINOR.PATCH. Must be aligned with an existing repository tag | [optional] [default to null] |
| **ownerId** | **String** | the User id which own this Solution | [optional] [default to null] |
| **sdkVersion** | **String** | the MAJOR.MINOR version used to build this solution | [optional] [default to null] |
| **url** | **String** | an optional URL link to solution page | [optional] [default to null] |
| **tags** | **List** | the list of tags | [optional] [default to null] |
| **parameters** | [**List**](RunTemplateParameter.md) | the list of Run Template Parameters | [optional] [default to null] |
| **parameterGroups** | [**List**](RunTemplateParameterGroup.md) | the list of parameters groups for the Run Templates | [optional] [default to null] |
| **runTemplates** | [**List**](RunTemplate.md) | list of Run Template | [default to []] |
| **security** | [**SolutionSecurity**](SolutionSecurity.md) |  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

