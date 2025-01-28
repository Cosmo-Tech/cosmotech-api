# WorkspaceUpdateRequest
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **key** | **String** | technical key for resource name convention and version grouping. Must be unique | [optional] [default to null] |
| **name** | **String** | Workspace name | [optional] [default to null] |
| **description** | **String** | the Workspace description | [optional] [default to null] |
| **tags** | **List** | the list of tags | [optional] [default to null] |
| **solution** | [**WorkspaceSolution**](WorkspaceSolution.md) |  | [optional] [default to null] |
| **webApp** | [**WorkspaceWebApp**](WorkspaceWebApp.md) |  | [optional] [default to null] |
| **sendInputToDataWarehouse** | **Boolean** | default setting for all Scenarios and Run Templates | [optional] [default to null] |
| **useDedicatedEventHubNamespace** | **Boolean** | Set this property to true to use a dedicated Azure Event Hub Namespace | [optional] [default to null] |
| **dedicatedEventHubSasKeyName** | **String** | the Dedicated Event Hub SAS key name | [optional] [default to null] |
| **dedicatedEventHubAuthenticationStrategy** | **String** | the Event Hub authentication strategy | [optional] [default to null] |
| **sendScenarioRunToEventHub** | **Boolean** | default setting for all Scenarios and Run Templates | [optional] [default to null] |
| **sendScenarioMetadataToEventHub** | **Boolean** | Set this property to false to not send scenario metada | [optional] [default to null] |
| **datasetCopy** | **Boolean** | Activate the copy of dataset on scenario creation | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

