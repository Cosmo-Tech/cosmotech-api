# WorkspaceApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createDatasetLink**](WorkspaceApi.md#createDatasetLink) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/link |  |
| [**createWorkspace**](WorkspaceApi.md#createWorkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace |
| [**createWorkspaceAccessControl**](WorkspaceApi.md#createWorkspaceAccessControl) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/access | Add a control access to the Workspace |
| [**createWorkspaceFile**](WorkspaceApi.md#createWorkspaceFile) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/files | Upload a file for the Workspace |
| [**deleteDatasetLink**](WorkspaceApi.md#deleteDatasetLink) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/link |  |
| [**deleteWorkspace**](WorkspaceApi.md#deleteWorkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace |
| [**deleteWorkspaceAccessControl**](WorkspaceApi.md#deleteWorkspaceAccessControl) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Remove the specified access from the given Organization Workspace |
| [**deleteWorkspaceFile**](WorkspaceApi.md#deleteWorkspaceFile) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files/delete | Delete a workspace file |
| [**deleteWorkspaceFiles**](WorkspaceApi.md#deleteWorkspaceFiles) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files | Delete all Workspace files |
| [**getWorkspace**](WorkspaceApi.md#getWorkspace) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of an workspace |
| [**getWorkspaceAccessControl**](WorkspaceApi.md#getWorkspaceAccessControl) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Get a control access for the Workspace |
| [**getWorkspaceFile**](WorkspaceApi.md#getWorkspaceFile) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files/download | Download the Workspace File specified |
| [**getWorkspaceSecurity**](WorkspaceApi.md#getWorkspaceSecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security | Get the Workspace security information |
| [**listWorkspaceFiles**](WorkspaceApi.md#listWorkspaceFiles) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files | List all Workspace files |
| [**listWorkspaceRolePermissions**](WorkspaceApi.md#listWorkspaceRolePermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/permissions/{role} | Get the Workspace permission by given role |
| [**listWorkspaceSecurityUsers**](WorkspaceApi.md#listWorkspaceSecurityUsers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/users | Get the Workspace security users list |
| [**listWorkspaces**](WorkspaceApi.md#listWorkspaces) | **GET** /organizations/{organization_id}/workspaces | List all Workspaces |
| [**updateWorkspace**](WorkspaceApi.md#updateWorkspace) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id} | Update a workspace |
| [**updateWorkspaceAccessControl**](WorkspaceApi.md#updateWorkspaceAccessControl) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Update the specified access to User for a Workspace |
| [**updateWorkspaceDefaultSecurity**](WorkspaceApi.md#updateWorkspaceDefaultSecurity) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/security/default | Update the Workspace default security |


<a name="createDatasetLink"></a>
# **createDatasetLink**
> Workspace createDatasetLink(organization\_id, workspace\_id, datasetId)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **datasetId** | **String**| dataset id to be linked to | [default to null] |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="createWorkspace"></a>
# **createWorkspace**
> Workspace createWorkspace(organization\_id, WorkspaceCreateRequest)

Create a new workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **WorkspaceCreateRequest** | [**WorkspaceCreateRequest**](../Models/WorkspaceCreateRequest.md)| the Workspace to create | |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="createWorkspaceAccessControl"></a>
# **createWorkspaceAccessControl**
> WorkspaceAccessControl createWorkspaceAccessControl(organization\_id, workspace\_id, WorkspaceAccessControl)

Add a control access to the Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **WorkspaceAccessControl** | [**WorkspaceAccessControl**](../Models/WorkspaceAccessControl.md)| the new Workspace security access to add. | |

### Return type

[**WorkspaceAccessControl**](../Models/WorkspaceAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="createWorkspaceFile"></a>
# **createWorkspaceFile**
> WorkspaceFile createWorkspaceFile(organization\_id, workspace\_id, file, overwrite, destination)

Upload a file for the Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **file** | **File**| The file to upload | [default to null] |
| **overwrite** | **Boolean**| Whether to overwrite an existing file | [optional] [default to false] |
| **destination** | **String**| Destination path. Must end with a &#39;/&#39; if specifying a folder. Note that paths may or may not start with a &#39;/&#39;, but they are always treated as relative to the Workspace root location.  | [optional] [default to null] |

### Return type

[**WorkspaceFile**](../Models/WorkspaceFile.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: multipart/form-data
- **Accept**: application/json

<a name="deleteDatasetLink"></a>
# **deleteDatasetLink**
> deleteDatasetLink(organization\_id, workspace\_id, datasetId)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **datasetId** | **String**| dataset id to be linked to | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteWorkspace"></a>
# **deleteWorkspace**
> deleteWorkspace(organization\_id, workspace\_id)

Delete a workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteWorkspaceAccessControl"></a>
# **deleteWorkspaceAccessControl**
> deleteWorkspaceAccessControl(organization\_id, workspace\_id, identity\_id)

Remove the specified access from the given Organization Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteWorkspaceFile"></a>
# **deleteWorkspaceFile**
> deleteWorkspaceFile(organization\_id, workspace\_id, file\_name)

Delete a workspace file

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **file\_name** | **String**| the file name | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteWorkspaceFiles"></a>
# **deleteWorkspaceFiles**
> deleteWorkspaceFiles(organization\_id, workspace\_id)

Delete all Workspace files

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="getWorkspace"></a>
# **getWorkspace**
> Workspace getWorkspace(organization\_id, workspace\_id)

Get the details of an workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getWorkspaceAccessControl"></a>
# **getWorkspaceAccessControl**
> WorkspaceAccessControl getWorkspaceAccessControl(organization\_id, workspace\_id, identity\_id)

Get a control access for the Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

[**WorkspaceAccessControl**](../Models/WorkspaceAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getWorkspaceFile"></a>
# **getWorkspaceFile**
> File getWorkspaceFile(organization\_id, workspace\_id, file\_name)

Download the Workspace File specified

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **file\_name** | **String**| the file name | [default to null] |

### Return type

**File**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/octet-stream

<a name="getWorkspaceSecurity"></a>
# **getWorkspaceSecurity**
> WorkspaceSecurity getWorkspaceSecurity(organization\_id, workspace\_id)

Get the Workspace security information

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |

### Return type

[**WorkspaceSecurity**](../Models/WorkspaceSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listWorkspaceFiles"></a>
# **listWorkspaceFiles**
> List listWorkspaceFiles(organization\_id, workspace\_id)

List all Workspace files

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |

### Return type

[**List**](../Models/WorkspaceFile.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listWorkspaceRolePermissions"></a>
# **listWorkspaceRolePermissions**
> List listWorkspaceRolePermissions(organization\_id, workspace\_id, role)

Get the Workspace permission by given role

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **role** | **String**| the Role | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listWorkspaceSecurityUsers"></a>
# **listWorkspaceSecurityUsers**
> List listWorkspaceSecurityUsers(organization\_id, workspace\_id)

Get the Workspace security users list

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listWorkspaces"></a>
# **listWorkspaces**
> List listWorkspaces(organization\_id, page, size)

List all Workspaces

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **page** | **Integer**| page number to query (first page is at index 0) | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="updateWorkspace"></a>
# **updateWorkspace**
> Workspace updateWorkspace(organization\_id, workspace\_id, WorkspaceUpdateRequest)

Update a workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **WorkspaceUpdateRequest** | [**WorkspaceUpdateRequest**](../Models/WorkspaceUpdateRequest.md)| The new Workspace details. This endpoint can&#39;t be used to update security | |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateWorkspaceAccessControl"></a>
# **updateWorkspaceAccessControl**
> WorkspaceAccessControl updateWorkspaceAccessControl(organization\_id, workspace\_id, identity\_id, WorkspaceRole)

Update the specified access to User for a Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |
| **WorkspaceRole** | [**WorkspaceRole**](../Models/WorkspaceRole.md)| The new Workspace Access Control | |

### Return type

[**WorkspaceAccessControl**](../Models/WorkspaceAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="updateWorkspaceDefaultSecurity"></a>
# **updateWorkspaceDefaultSecurity**
> WorkspaceSecurity updateWorkspaceDefaultSecurity(organization\_id, workspace\_id, WorkspaceRole)

Update the Workspace default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **WorkspaceRole** | [**WorkspaceRole**](../Models/WorkspaceRole.md)| This change the workspace default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the workspace. | |

### Return type

[**WorkspaceSecurity**](../Models/WorkspaceSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

