# RunApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**deleteRun**](RunApi.md#deleteRun) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id} | Delete a run |
| [**getRun**](RunApi.md#getRun) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id} | Get the details of a run |
| [**getRunLogs**](RunApi.md#getRunLogs) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/logs | get the logs for the Run |
| [**getRunStatus**](RunApi.md#getRunStatus) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/status | get the status for the Run |
| [**listRuns**](RunApi.md#listRuns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs | get the list of Runs for the Runner |


<a name="deleteRun"></a>
# **deleteRun**
> deleteRun(organization\_id, workspace\_id, runner\_id, run\_id)

Delete a run

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **runner\_id** | **String**| The Runner identifier | [default to null] |
| **run\_id** | **String**| The Run identifier | [default to null] |

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

    Retrieve detailed information about a specific run including state, parameters used, dataset list, execution timestamps, and container configuration.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **runner\_id** | **String**| The Runner identifier | [default to null] |
| **run\_id** | **String**| The Run identifier | [default to null] |

### Return type

[**Run**](../Models/Run.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="getRunLogs"></a>
# **getRunLogs**
> String getRunLogs(organization\_id, workspace\_id, runner\_id, run\_id)

get the logs for the Run

    Retrieve execution logs for a run as plain text. Logs are aggregated from all containers. May be truncated for long-running simulations.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **runner\_id** | **String**| The Runner identifier | [default to null] |
| **run\_id** | **String**| The Run identifier | [default to null] |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: text/plain

<a name="getRunStatus"></a>
# **getRunStatus**
> RunStatus getRunStatus(organization\_id, workspace\_id, runner\_id, run\_id)

get the status for the Run

    Retrieve detailed execution status of a run including workflow phase, progress, individual node states, and estimated completion time.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **runner\_id** | **String**| The Runner identifier | [default to null] |
| **run\_id** | **String**| The Run identifier | [default to null] |

### Return type

[**RunStatus**](../Models/RunStatus.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listRuns"></a>
# **listRuns**
> List listRuns(organization\_id, workspace\_id, runner\_id, page, size)

get the list of Runs for the Runner

    Retrieve a paginated list of all runs for a specific runner, ordered by creation time (newest first). Includes run state, timestamps, and basic metadata.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **runner\_id** | **String**| The Runner identifier | [default to null] |
| **page** | **Integer**| page number to query (first page is at index 0) | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Run.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

