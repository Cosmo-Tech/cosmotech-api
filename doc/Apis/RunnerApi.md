# RunnerApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createRunner**](RunnerApi.md#createRunner) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners | Create a new Runner |
| [**createRunnerAccessControl**](RunnerApi.md#createRunnerAccessControl) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access | Add a control access to the Runner |
| [**deleteRunner**](RunnerApi.md#deleteRunner) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Delete a runner |
| [**deleteRunnerAccessControl**](RunnerApi.md#deleteRunnerAccessControl) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Remove the specified access from the given Runner |
| [**getRunner**](RunnerApi.md#getRunner) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Get the details of a runner |
| [**getRunnerAccessControl**](RunnerApi.md#getRunnerAccessControl) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Get a control access for the Runner |
| [**getRunnerSecurity**](RunnerApi.md#getRunnerSecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security | Get the Runner security information |
| [**listRunnerPermissions**](RunnerApi.md#listRunnerPermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/permissions/{role} | Get the Runner permission by given role |
| [**listRunnerSecurityUsers**](RunnerApi.md#listRunnerSecurityUsers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/users | Get the Runner security users list |
| [**listRunners**](RunnerApi.md#listRunners) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners | List all Runners |
| [**startRun**](RunnerApi.md#startRun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/start | Start a run with runner parameters |
| [**stopRun**](RunnerApi.md#stopRun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/stop | Stop the last run |
| [**updateRunner**](RunnerApi.md#updateRunner) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Update a runner |
| [**updateRunnerAccessControl**](RunnerApi.md#updateRunnerAccessControl) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Update the specified access to User for a Runner |
| [**updateRunnerDefaultSecurity**](RunnerApi.md#updateRunnerDefaultSecurity) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/default | Set the Runner default security |


<a name="createRunner"></a>
# **createRunner**
> Runner createRunner(organization\_id, workspace\_id, RunnerCreateRequest)

Create a new Runner

    Create a new runner for executing simulations. Use parentId to create a child runner that inherits configuration from a parent.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **RunnerCreateRequest** | [**RunnerCreateRequest**](../Models/RunnerCreateRequest.md)| the Runner to create | |

### Return type

[**Runner**](../Models/Runner.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="createRunnerAccessControl"></a>
# **createRunnerAccessControl**
> RunnerAccessControl createRunnerAccessControl(organization\_id, workspace\_id, runner\_id, RunnerAccessControl)

Add a control access to the Runner

    Grant access to a runner for a user or group. Valid roles: viewer, editor, validator (can validate runs), admin.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **RunnerAccessControl** | [**RunnerAccessControl**](../Models/RunnerAccessControl.md)| the new Runner security access to add. | |

### Return type

[**RunnerAccessControl**](../Models/RunnerAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="deleteRunner"></a>
# **deleteRunner**
> deleteRunner(organization\_id, workspace\_id, runner\_id)

Delete a runner

    Delete a runner. Cannot delete while runs are in progress. Note: Child runners that reference this runner are not deleted automatically.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteRunnerAccessControl"></a>
# **deleteRunnerAccessControl**
> deleteRunnerAccessControl(organization\_id, workspace\_id, runner\_id, identity\_id)

Remove the specified access from the given Runner

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="getRunner"></a>
# **getRunner**
> Runner getRunner(organization\_id, workspace\_id, runner\_id)

Get the details of a runner

    Retrieve detailed information about a runner.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |

### Return type

[**Runner**](../Models/Runner.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="getRunnerAccessControl"></a>
# **getRunnerAccessControl**
> RunnerAccessControl getRunnerAccessControl(organization\_id, workspace\_id, runner\_id, identity\_id)

Get a control access for the Runner

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

[**RunnerAccessControl**](../Models/RunnerAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="getRunnerSecurity"></a>
# **getRunnerSecurity**
> RunnerSecurity getRunnerSecurity(organization\_id, workspace\_id, runner\_id)

Get the Runner security information

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |

### Return type

[**RunnerSecurity**](../Models/RunnerSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listRunnerPermissions"></a>
# **listRunnerPermissions**
> List listRunnerPermissions(organization\_id, workspace\_id, runner\_id, role)

Get the Runner permission by given role

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **role** | **String**| the Role | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listRunnerSecurityUsers"></a>
# **listRunnerSecurityUsers**
> List listRunnerSecurityUsers(organization\_id, workspace\_id, runner\_id)

Get the Runner security users list

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listRunners"></a>
# **listRunners**
> List listRunners(organization\_id, workspace\_id, page, size)

List all Runners

    Retrieve a paginated list of all runners in a workspace.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **page** | **Integer**| Page number to query (first page is at index 0) | [optional] [default to null] |
| **size** | **Integer**| Amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Runner.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="startRun"></a>
# **startRun**
> CreatedRun startRun(organization\_id, workspace\_id, runner\_id)

Start a run with runner parameters

    Start a new simulation run using the runner&#39;s current configuration. Returns the run Id. The run executes asynchronously - use the run status endpoint to monitor progress

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |

### Return type

[**CreatedRun**](../Models/CreatedRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="stopRun"></a>
# **stopRun**
> stopRun(organization\_id, workspace\_id, runner\_id)

Stop the last run

    Stop the currently executing run for this runner. The stop operation is asynchronous - the run may continue briefly before stopping.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="updateRunner"></a>
# **updateRunner**
> Runner updateRunner(organization\_id, workspace\_id, runner\_id, RunnerUpdateRequest)

Update a runner

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **RunnerUpdateRequest** | [**RunnerUpdateRequest**](../Models/RunnerUpdateRequest.md)| the new Runner details. This endpoint can&#39;t be used to update security | |

### Return type

[**Runner**](../Models/Runner.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="updateRunnerAccessControl"></a>
# **updateRunnerAccessControl**
> RunnerAccessControl updateRunnerAccessControl(organization\_id, workspace\_id, runner\_id, identity\_id, RunnerRole)

Update the specified access to User for a Runner

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |
| **RunnerRole** | [**RunnerRole**](../Models/RunnerRole.md)| The new Runner Access Control | |

### Return type

[**RunnerAccessControl**](../Models/RunnerAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="updateRunnerDefaultSecurity"></a>
# **updateRunnerDefaultSecurity**
> RunnerSecurity updateRunnerDefaultSecurity(organization\_id, workspace\_id, runner\_id, RunnerRole)

Set the Runner default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **RunnerRole** | [**RunnerRole**](../Models/RunnerRole.md)| This change the runner default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the runner. | |

### Return type

[**RunnerSecurity**](../Models/RunnerSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

