# Solution
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | [**String**](string.md) | the Solution version unique identifier | [optional] [default to null]
**key** | [**String**](string.md) | the Solution key which group Solution versions | [default to null]
**name** | [**String**](string.md) | the Solution name | [default to null]
**description** | [**String**](string.md) | the Solution description | [optional] [default to null]
**repository** | [**String**](string.md) | the registry repository containing the image | [default to null]
**csmSimulator** | [**String**](string.md) | the main Cosmo Tech simulator name used in standard Run Template | [optional] [default to null]
**version** | [**String**](string.md) | the Solution version MAJOR.MINOR.PATCH. Must be aligned with an existing repository tag | [default to null]
**ownerId** | [**String**](string.md) | the User id which own this Solution | [optional] [default to null]
**url** | [**String**](string.md) | an optional URL link to solution page | [optional] [default to null]
**tags** | [**List**](string.md) | the list of tags | [optional] [default to null]
**parameters** | [**List**](RunTemplateParameter.md) | the list of Run Template Parameters | [optional] [default to null]
**parameterGroups** | [**List**](RunTemplateParameterGroup.md) | the list of parameters groups for the Run Templates | [optional] [default to null]
**runTemplates** | [**List**](RunTemplate.md) | list of Run Template | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

