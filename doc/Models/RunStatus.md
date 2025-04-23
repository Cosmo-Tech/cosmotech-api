# RunStatus
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | the Run id | [optional] [default to null] |
| **organizationId** | **String** | the Organization id | [optional] [default to null] |
| **workspaceId** | **String** | the Workspace id | [optional] [default to null] |
| **runnerId** | **String** | the Runner id | [optional] [default to null] |
| **workflowId** | **String** | the Cosmo Tech compute cluster Argo Workflow Id to search | [optional] [default to null] |
| **workflowName** | **String** | the Cosmo Tech compute cluster Argo Workflow Name | [optional] [default to null] |
| **createInfo** | [**RunEditInfo**](RunEditInfo.md) | The timestamp of the Run creation in milliseconds | [optional] [default to null] |
| **startTime** | **String** | the Run start Date Time | [optional] [default to null] |
| **endTime** | **String** | the Run end Date Time | [optional] [default to null] |
| **phase** | **String** | high-level summary of where the workflow is in its lifecycle | [optional] [default to null] |
| **progress** | **String** | progress to completion | [optional] [default to null] |
| **message** | **String** | a  human readable message indicating details about why the workflow is in this condition | [optional] [default to null] |
| **estimatedDuration** | **Integer** | estimatedDuration in seconds | [optional] [default to null] |
| **nodes** | [**List**](RunStatusNode.md) | status of Run nodes | [optional] [default to null] |
| **state** | [**RunState**](RunState.md) |  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

