# ScenarioRunContainerLogs
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | [**String**](string.md) | the container Id | [optional] [default to null]
**envVars** | [**Map**](object.md) | a freeform environment variable map | [optional] [default to null]
**image** | [**String**](string.md) | the container image URI | [optional] [default to null]
**runArgs** | [**List**](string.md) | the list of run arguments for the container | [optional] [default to null]
**computer** | [**String**](string.md) | computer/node that&#39;s generating the log | [optional] [default to null]
**logs** | [**List**](ScenarioRunContainerLog.md) | the list of container logs in structured format | [optional] [default to null]
**textLog** | [**String**](string.md) | the plain text log if plainText option has been set | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

