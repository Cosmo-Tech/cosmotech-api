# RunApi

All URIs are relative to *https://dev.api.cosmotech.com*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**deleteRun**](RunApi.md#deleteRun) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id} | Delete a run |
| [**getRun**](RunApi.md#getRun) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id} | Get the details of a run |
| [**getRunLogs**](RunApi.md#getRunLogs) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/logs | get the logs for the Run |
| [**getRunStatus**](RunApi.md#getRunStatus) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/status | get the status for the Run |
| [**listRuns**](RunApi.md#listRuns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs | get the list of Runs for the Runner |
| [**queryResultDatas**](RunApi.md#queryResultDatas) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/results/query | query the resultDatas in the run |
| [**upsertResultData**](RunApi.md#upsertResultData) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/results/send | create or update the ResultDatas |


<a name="deleteRun"></a>
# **deleteRun**
> deleteRun(organization\_id, workspace\_id, runner\_id, run\_id)

Delete a run

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **run\_id** | **String**| the Run identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="getRun"></a>
# **getRun**
> Run getRun(organization\_id, workspace\_id, runner\_id, run\_id)

Get the details of a run

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **run\_id** | **String**| the Run identifier | [default to null] |

### Return type

[**Run**](../Models/Run.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getRunLogs"></a>
# **getRunLogs**
> RunLogs getRunLogs(organization\_id, workspace\_id, runner\_id, run\_id)

get the logs for the Run

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **run\_id** | **String**| the Run identifier | [default to null] |

### Return type

[**RunLogs**](../Models/RunLogs.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getRunStatus"></a>
# **getRunStatus**
> RunStatus getRunStatus(organization\_id, workspace\_id, runner\_id, run\_id)

get the status for the Run

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **run\_id** | **String**| the Run identifier | [default to null] |

### Return type

[**RunStatus**](../Models/RunStatus.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listRuns"></a>
# **listRuns**
> List listRuns(organization\_id, workspace\_id, runner\_id, page, size)

get the list of Runs for the Runner

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Run.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="queryResultDatas"></a>
# **queryResultDatas**
> ResultData queryResultDatas(organization\_id, workspace\_id, runner\_id, run\_id, ResultDataQuery, page, size)

query the resultDatas in the run

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **run\_id** | **String**| the Run identifier | [default to null] |
| **ResultDataQuery** | [**ResultDataQuery**](../Models/ResultDataQuery.md)| the query to run | |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**ResultData**](../Models/ResultData.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="upsertResultData"></a>
# **upsertResultData**
> ResultData upsertResultData(organization\_id, workspace\_id, runner\_id, run\_id, ResultData, page, size)

create or update the ResultDatas

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **run\_id** | **String**| the Run identifier | [default to null] |
| **ResultData** | [**ResultData**](../Models/ResultData.md)| the ResultDatas to register | |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**ResultData**](../Models/ResultData.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

