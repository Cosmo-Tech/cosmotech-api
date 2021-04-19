# Workspace
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | [**String**](string.md) | the Workspace version unique identifier | [optional] [default to null]
**name** | [**String**](string.md) | the Workspace name | [default to null]
**description** | [**String**](string.md) | the Workspace description | [optional] [default to null]
**version** | [**String**](string.md) | the Workspace version MAJOR.MINOR.PATCH. | [optional] [default to null]
**tags** | [**List**](string.md) | the list of tags | [optional] [default to null]
**ownerId** | [**String**](string.md) | the user id which own this workspace | [optional] [default to null]
**solution** | [**WorkspaceSolution**](WorkspaceSolution.md) |  | [default to null]
**users** | [**List**](WorkspaceUser.md) | the list of users Id with their role | [optional] [default to null]
**webApp** | [**WorkspaceWebApp**](WorkspaceWebApp.md) |  | [optional] [default to null]
**services** | [**WorkspaceServices**](WorkspaceServices.md) |  | [optional] [default to null]
**sendInputToDataWarehouse** | [**Boolean**](boolean.md) | default setting for all Scenarios and Run Templates to set whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to Simulation Run | [optional] [default to true]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

