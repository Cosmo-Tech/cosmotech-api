# Connector
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | the Connector version unique identifier | [optional] [default to null] |
| **key** | **String** | the Connector key which group Connector versions | [optional] [default to null] |
| **name** | **String** | the Connector name | [optional] [default to null] |
| **description** | **String** | the Connector description | [optional] [default to null] |
| **repository** | **String** | the registry repository containing the image | [optional] [default to null] |
| **version** | **String** | the Connector version MAJOR.MINOR.PATCH. Must be aligned with an existing repository tag | [optional] [default to null] |
| **tags** | **List** | the list of tags | [optional] [default to null] |
| **ownerId** | **String** | the user id which own this connector version | [optional] [default to null] |
| **url** | **String** | an optional URL link to connector page | [optional] [default to null] |
| **ioTypes** | [**List**](ioTypesEnum.md) |  | [optional] [default to null] |
| **parameterGroups** | [**List**](ConnectorParameterGroup.md) | the list of connector parameters groups | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

