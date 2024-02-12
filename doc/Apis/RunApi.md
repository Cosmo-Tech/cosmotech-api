# RunApi

All URIs are relative to *https://dev.api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**deleteRun**](RunApi.md#deleteRun) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/runs/{run_id} | Delete a run
[**findRunById**](RunApi.md#findRunById) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/runs/{run_id} | Get the details of a run
[**getRunCumulatedLogs**](RunApi.md#getRunCumulatedLogs) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/runs/{run_id}/cumulatedlogs | Get the cumulated logs of a run
[**getRunLogs**](RunApi.md#getRunLogs) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/runs/{run_id}/logs | get the logs for the Run
[**getRunStatus**](RunApi.md#getRunStatus) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/runs/{run_id}/status | get the status for the Run
[**getRuns**](RunApi.md#getRuns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/runs | get the list of Runs for the Scenario


<a name="deleteRun"></a>
# **deleteRun**
> deleteRun(organization\_id, workspace\_id, scenario\_id, run\_id)

Delete a run

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **run\_id** | **String**| the Run identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="findRunById"></a>
# **findRunById**
> Run findRunById(organization\_id, workspace\_id, scenario\_id, run\_id)

Get the details of a run

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **run\_id** | **String**| the Run identifier | [default to null]

### Return type

[**Run**](../Models/Run.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getRunCumulatedLogs"></a>
# **getRunCumulatedLogs**
> String getRunCumulatedLogs(organization\_id, workspace\_id, scenario\_id, run\_id)

Get the cumulated logs of a run

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **run\_id** | **String**| the Run identifier | [default to null]

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: text/plain

<a name="getRunLogs"></a>
# **getRunLogs**
> RunLogs getRunLogs(organization\_id, workspace\_id, scenario\_id, run\_id)

get the logs for the Run

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **run\_id** | **String**| the Run identifier | [default to null]

### Return type

[**RunLogs**](../Models/RunLogs.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getRunStatus"></a>
# **getRunStatus**
> RunStatus getRunStatus(organization\_id, workspace\_id, scenario\_id, run\_id)

get the status for the Run

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **run\_id** | **String**| the Run identifier | [default to null]

### Return type

[**RunStatus**](../Models/RunStatus.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getRuns"></a>
# **getRuns**
> List getRuns(organization\_id, workspace\_id, scenario\_id, page, size)

get the list of Runs for the Scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **page** | **Integer**| page number to query | [optional] [default to null]
 **size** | **Integer**| amount of result by page | [optional] [default to null]

### Return type

[**List**](../Models/Run.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

