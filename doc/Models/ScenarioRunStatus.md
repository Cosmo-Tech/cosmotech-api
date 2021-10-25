# ScenarioRunStatus
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | the ScenarioRun id | [optional] [default to null]
**organizationId** | **String** | the ScenarioRun id | [optional] [default to null]
**workflowId** | **String** | the Cosmo Tech compute cluster Argo Workflow Id to search | [optional] [default to null]
**workflowName** | **String** | the Cosmo Tech compute cluster Argo Workflow Name | [optional] [default to null]
**startTime** | **String** | the ScenarioRun start Date Time | [optional] [default to null]
**endTime** | **String** | the ScenarioRun end Date Time | [optional] [default to null]
**phase** | **String** | high-level summary of where the workflow is in its lifecycle | [optional] [default to null]
**progress** | **String** | progress to completion | [optional] [default to null]
**message** | **String** | a  human readable message indicating details about why the workflow is in this condition | [optional] [default to null]
**estimatedDuration** | **Integer** | estimatedDuration in seconds | [optional] [default to null]
**nodes** | [**List**](ScenarioRunStatusNode.md) | status of ScenarioRun nodes | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

