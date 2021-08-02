# Connector
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | [**String**](string.md) | the Connector version unique identifier | [optional] [default to null]
**key** | [**String**](string.md) | the Connector key which group Connector versions | [default to null]
**name** | [**String**](string.md) | the Connector name | [default to null]
**description** | [**String**](string.md) | the Connector description | [optional] [default to null]
**repository** | [**String**](string.md) | the registry repository containing the image | [default to null]
**version** | [**String**](string.md) | the Connector version MAJOR.MINOR.PATCH. Must be aligned with an existing repository tag | [default to null]
**tags** | [**List**](string.md) | the list of tags | [optional] [default to null]
**ownerId** | [**String**](string.md) | the user id which own this connector version | [optional] [default to null]
**url** | [**String**](string.md) | an optional URL link to connector page | [optional] [default to null]
**azureManagedIdentity** | [**Boolean**](boolean.md) | whether or not the connector uses Azure Managed Identity | [optional] [default to null]
**azureAuthenticationWithCustomerAppRegistration** | [**Boolean**](boolean.md) | whether to authenticate against Azure using the app registration credentials provided by the customer | [optional] [default to null]
**ioTypes** | [**List**](string.md) |  | [default to null]
**parameterGroups** | [**List**](ConnectorParameterGroup.md) | the list of connector parameters groups | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

