# Workspace
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | the Workspace version unique identifier | [optional] [default to null]
**organizationId** | **String** | the Organization unique identifier | [optional] [default to null]
**key** | **String** | technical key for resource name convention and version grouping. Must be unique | [default to null]
**name** | **String** | the Workspace name | [default to null]
**description** | **String** | the Workspace description | [optional] [default to null]
**version** | **String** | the Workspace version MAJOR.MINOR.PATCH. | [optional] [default to null]
**tags** | **List** | the list of tags | [optional] [default to null]
**ownerId** | **String** | the user id which own this workspace | [optional] [default to null]
**solution** | [**WorkspaceSolution**](WorkspaceSolution.md) |  | [default to null]
**webApp** | [**WorkspaceWebApp**](WorkspaceWebApp.md) |  | [optional] [default to null]
**sendInputToDataWarehouse** | **Boolean** | default setting for all Scenarios and Run Templates to set whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to the ScenarioRun | [optional] [default to null]
**security** | [**WorkspaceSecurity**](WorkspaceSecurity.md) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

