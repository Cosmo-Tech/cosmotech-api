# RunContainer
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | the container Id | [optional] [default to null] |
| **name** | **String** | the container name | [default to null] |
| **labels** | **Map** | the metadata labels | [optional] [default to null] |
| **envVars** | **Map** | environment variable map | [optional] [default to null] |
| **image** | **String** | the container image URI | [default to null] |
| **entrypoint** | **String** | the container entry point | [optional] [default to null] |
| **runArgs** | **List** | the list of run arguments for the container | [optional] [default to null] |
| **dependencies** | **List** | the list of dependencies container name to run this container | [optional] [default to null] |
| **solutionContainer** | **Boolean** | whether or not this container is a Cosmo Tech solution container | [optional] [default to null] |
| **nodeLabel** | **String** | the node label request | [optional] [default to null] |
| **runSizing** | [**ContainerResourceSizing**](ContainerResourceSizing.md) |  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

