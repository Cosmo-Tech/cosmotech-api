# RunnerApi

All URIs are relative to *https://dev.api.cosmotech.com*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addRunnerAccessControl**](RunnerApi.md#addRunnerAccessControl) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access | Add a control access to the Runner |
| [**createRunner**](RunnerApi.md#createRunner) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners | Create a new Runner |
| [**deleteRunner**](RunnerApi.md#deleteRunner) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Delete a runner |
| [**getRunner**](RunnerApi.md#getRunner) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Get the details of an runner |
| [**getRunnerAccessControl**](RunnerApi.md#getRunnerAccessControl) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Get a control access for the Runner |
| [**getRunnerPermissions**](RunnerApi.md#getRunnerPermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/permissions/{role} | Get the Runner permission by given role |
| [**getRunnerSecurity**](RunnerApi.md#getRunnerSecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security | Get the Runner security information |
| [**getRunnerSecurityUsers**](RunnerApi.md#getRunnerSecurityUsers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/users | Get the Runner security users list |
| [**listRunners**](RunnerApi.md#listRunners) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/runners | List all Runners |
| [**removeRunnerAccessControl**](RunnerApi.md#removeRunnerAccessControl) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Remove the specified access from the given Organization Runner |
| [**setRunnerDefaultSecurity**](RunnerApi.md#setRunnerDefaultSecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/default | Set the Runner default security |
| [**startRun**](RunnerApi.md#startRun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/start | Start a run with runner parameters |
| [**stopRun**](RunnerApi.md#stopRun) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/stop | Stop the last run |
| [**updateRunner**](RunnerApi.md#updateRunner) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id} | Update a runner |
| [**updateRunnerAccessControl**](RunnerApi.md#updateRunnerAccessControl) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id} | Update the specified access to User for a Runner |


<a name="addRunnerAccessControl"></a>
# **addRunnerAccessControl**
> RunnerAccessControl addRunnerAccessControl(organization\_id, workspace\_id, runner\_id, RunnerAccessControl)

Add a control access to the Runner

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
- **Accept**: application/json

<a name="createRunner"></a>
# **createRunner**
> Runner createRunner(organization\_id, workspace\_id, Runner)

Create a new Runner

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **Runner** | [**Runner**](../Models/Runner.md)| the Runner to create | |

### Return type

[**Runner**](../Models/Runner.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="deleteRunner"></a>
# **deleteRunner**
> deleteRunner(organization\_id, workspace\_id, runner\_id)

Delete a runner

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

<a name="getRunner"></a>
# **getRunner**
> Runner getRunner(organization\_id, workspace\_id, runner\_id)

Get the details of an runner

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
- **Accept**: application/json

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
- **Accept**: application/json

<a name="getRunnerPermissions"></a>
# **getRunnerPermissions**
> List getRunnerPermissions(organization\_id, workspace\_id, runner\_id, role)

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
- **Accept**: application/json

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
- **Accept**: application/json

<a name="getRunnerSecurityUsers"></a>
# **getRunnerSecurityUsers**
> List getRunnerSecurityUsers(organization\_id, workspace\_id, runner\_id)

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
- **Accept**: application/json

<a name="listRunners"></a>
# **listRunners**
> List listRunners(organization\_id, workspace\_id, page, size)

List all Runners

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Runner.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="removeRunnerAccessControl"></a>
# **removeRunnerAccessControl**
> removeRunnerAccessControl(organization\_id, workspace\_id, runner\_id, identity\_id)

Remove the specified access from the given Organization Runner

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

<a name="setRunnerDefaultSecurity"></a>
# **setRunnerDefaultSecurity**
> RunnerSecurity setRunnerDefaultSecurity(organization\_id, workspace\_id, runner\_id, RunnerRole)

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
- **Accept**: application/json

<a name="startRun"></a>
# **startRun**
> RunnerLastRun startRun(organization\_id, workspace\_id, runner\_id)

Start a run with runner parameters

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |

### Return type

[**RunnerLastRun**](../Models/RunnerLastRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="stopRun"></a>
# **stopRun**
> stopRun(organization\_id, workspace\_id, runner\_id)

Stop the last run

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
> Runner updateRunner(organization\_id, workspace\_id, runner\_id, Runner)

Update a runner

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **runner\_id** | **String**| the Runner identifier | [default to null] |
| **Runner** | [**Runner**](../Models/Runner.md)| The new Runner details. This endpoint can&#39;t be used to update :   - id    - ownerId   - organizationId   - workspaceId   - creationDate   - security  | |

### Return type

[**Runner**](../Models/Runner.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

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
- **Accept**: application/json

