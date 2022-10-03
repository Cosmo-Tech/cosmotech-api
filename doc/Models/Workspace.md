# Workspace
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | the Workspace version unique identifier | [optional] [default to null]
**key** | **String** | technical key for resource name convention and version grouping. Must be unique | [default to null]
**name** | **String** | the Workspace name | [default to null]
**description** | **String** | the Workspace description | [optional] [default to null]
**version** | **String** | the Workspace version MAJOR.MINOR.PATCH. | [optional] [default to null]
**tags** | **List** | the list of tags | [optional] [default to null]
**ownerId** | **String** | the user id which own this workspace | [optional] [default to null]
**solution** | [**WorkspaceSolution**](WorkspaceSolution.md) |  | [default to null]
**webApp** | [**WorkspaceWebApp**](WorkspaceWebApp.md) |  | [optional] [default to null]
**sendInputToDataWarehouse** | **Boolean** | default setting for all Scenarios and Run Templates to set whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to the ScenarioRun | [optional] [default to null]
**useDedicatedEventHubNamespace** | **Boolean** | Set this property to true to use a dedicated Azure Event Hub Namespace for this Workspace. The Event Hub Namespace must be named \\&#39;&lt;organization_id\\&gt;-&lt;workspace_id\\&gt;\\&#39; (in lower case). This Namespace must also contain two Event Hubs named \\&#39;probesmeasures\\&#39; and \\&#39;scenariorun\\&#39;. | [optional] [default to false]
**sendScenarioMetadataToEventHub** | **Boolean** | Set this property to false to not send scenario metada to Azure Event Hub Namespace for this Workspace. The Event Hub Namespace must be named \\&#39;&lt;organization_id\\&gt;-&lt;workspace_id\\&gt;\\&#39; (in lower case). This Namespace must also contain two Event Hubs named \\&#39;scenariometadata\\&#39; and \\&#39;scenariorunmetadata\\&#39;. | [optional] [default to false]
**security** | [**WorkspaceSecurity**](WorkspaceSecurity.md) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

