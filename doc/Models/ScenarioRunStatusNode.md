# ScenarioRunStatusNode
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | the node id | [optional] [default to null]
**name** | **String** | the node unique name | [optional] [default to null]
**containerName** | **String** | the ScenarioRun container name | [optional] [default to null]
**outboundNodes** | **List** | the list of outbound nodes | [optional] [default to null]
**resourcesDuration** | [**ScenarioRunResourceRequested**](ScenarioRunResourceRequested.md) |  | [optional] [default to null]
**estimatedDuration** | **Integer** | estimatedDuration in seconds | [optional] [default to null]
**hostNodeName** | **String** | HostNodeName name of the Kubernetes node on which the Pod is running, if applicable | [optional] [default to null]
**message** | **String** | a human readable message indicating details about why the node is in this condition | [optional] [default to null]
**phase** | **String** | high-level summary of where the node is in its lifecycle | [optional] [default to null]
**progress** | **String** | progress to completion | [optional] [default to null]
**startTime** | **String** | the node start time | [optional] [default to null]
**endTime** | **String** | the node end time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

