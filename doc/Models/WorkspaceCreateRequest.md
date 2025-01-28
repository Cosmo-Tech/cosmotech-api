# WorkspaceCreateRequest
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **key** | **String** | technical key for resource name convention and version grouping. Must be unique | [default to null] |
| **name** | **String** | Workspace name. This name is display in the sample webApp | [default to null] |
| **description** | **String** | the Workspace description | [optional] [default to null] |
| **version** | **String** | the Workspace version MAJOR.MINOR.PATCH. | [optional] [default to null] |
| **tags** | **List** | the list of tags | [optional] [default to null] |
| **solution** | [**WorkspaceSolution**](WorkspaceSolution.md) |  | [default to null] |
| **webApp** | [**WorkspaceWebApp**](WorkspaceWebApp.md) |  | [optional] [default to null] |
| **sendInputToDataWarehouse** | **Boolean** | default setting for all Scenarios and Run Templates to set whether or not the Dataset values and the input parameters values are send to the DataWarehouse prior to the ScenarioRun | [optional] [default to null] |
| **useDedicatedEventHubNamespace** | **Boolean** | Set this property to true to use a dedicated Azure Event Hub Namespace for this Workspace. | [optional] [default to false] |
| **dedicatedEventHubSasKeyName** | **String** | the Dedicated Event Hub SAS key name, default to RootManageSharedAccessKey | [optional] [default to null] |
| **dedicatedEventHubAuthenticationStrategy** | **String** | the Event Hub authentication strategy | [optional] [default to null] |
| **sendScenarioRunToEventHub** | **Boolean** | default setting for all Scenarios and Run Templates | [optional] [default to true] |
| **sendScenarioMetadataToEventHub** | **Boolean** | Set this property to false to not send scenario metada | [optional] [default to false] |
| **datasetCopy** | **Boolean** | Activate the copy of dataset on scenario creation | [optional] [default to true] |
| **security** | [**WorkspaceSecurity**](WorkspaceSecurity.md) |  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

