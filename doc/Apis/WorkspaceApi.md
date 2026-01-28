# WorkspaceApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createWorkspace**](WorkspaceApi.md#createWorkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace |
| [**createWorkspaceAccessControl**](WorkspaceApi.md#createWorkspaceAccessControl) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/access | Add a control access to the Workspace |
| [**createWorkspaceFile**](WorkspaceApi.md#createWorkspaceFile) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/files | Upload a file for the Workspace |
| [**deleteWorkspace**](WorkspaceApi.md#deleteWorkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace |
| [**deleteWorkspaceAccessControl**](WorkspaceApi.md#deleteWorkspaceAccessControl) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Remove the specified access from the given Workspace |
| [**deleteWorkspaceFile**](WorkspaceApi.md#deleteWorkspaceFile) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files/delete | Delete a workspace file |
| [**deleteWorkspaceFiles**](WorkspaceApi.md#deleteWorkspaceFiles) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files | Delete all Workspace files |
| [**getWorkspace**](WorkspaceApi.md#getWorkspace) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of a workspace |
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


<a name="createWorkspace"></a>
# **createWorkspace**
> Workspace createWorkspace(organization\_id, WorkspaceCreateRequest)

Create a new workspace

    Create a new workspace linked to a solution. Required: key (unique identifier), name, and solution configuration. The workspace key must be unique within the organization.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **WorkspaceCreateRequest** | [**WorkspaceCreateRequest**](../Models/WorkspaceCreateRequest.md)| The Workspace to create | |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="createWorkspaceAccessControl"></a>
# **createWorkspaceAccessControl**
> WorkspaceAccessControl createWorkspaceAccessControl(organization\_id, workspace\_id, WorkspaceAccessControl)

Add a control access to the Workspace

    Grant access to a workspace for a user or group. Valid roles: viewer, editor, admin. Returns 400 if user already has access.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **WorkspaceAccessControl** | [**WorkspaceAccessControl**](../Models/WorkspaceAccessControl.md)| The new Workspace security access to add. | |

### Return type

[**WorkspaceAccessControl**](../Models/WorkspaceAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="createWorkspaceFile"></a>
# **createWorkspaceFile**
> WorkspaceFile createWorkspaceFile(organization\_id, workspace\_id, file, overwrite, destination)

Upload a file for the Workspace

    Upload a file to workspace storage. Use &#39;destination&#39; to specify path, &#39;overwrite&#39; to replace existing files. Returns 400 if file exists and overwrite is false.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **file** | **File**| The file to upload | [default to null] |
| **overwrite** | **Boolean**| Whether to overwrite an existing file | [optional] [default to false] |
| **destination** | **String**| Destination path. Must end with a &#39;/&#39; if specifying a folder. Note that paths may or may not start with a &#39;/&#39;, but they are always treated as relative to the Workspace root location.  | [optional] [default to null] |

### Return type

[**WorkspaceFile**](../Models/WorkspaceFile.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: multipart/form-data
- **Accept**: application/json, application/yaml

<a name="deleteWorkspace"></a>
# **deleteWorkspace**
> deleteWorkspace(organization\_id, workspace\_id)

Delete a workspace

    Permanently delete a workspace. All datasets and runners within the workspace must be deleted first. This operation cannot be undone.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |

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

Remove the specified access from the given Workspace

    Remove a user&#39;s access to a workspace. Cannot remove the last administrator.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **identity\_id** | **String**| The User identifier | [default to null] |

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
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **file\_name** | **String**| The file name | [default to null] |

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
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |

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

Get the details of a workspace

    Retrieve detailed information about a workspace including its solution link, security settings, file storage info, and configuration.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="getWorkspaceAccessControl"></a>
# **getWorkspaceAccessControl**
> WorkspaceAccessControl getWorkspaceAccessControl(organization\_id, workspace\_id, identity\_id)

Get a control access for the Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **identity\_id** | **String**| The User identifier | [default to null] |

### Return type

[**WorkspaceAccessControl**](../Models/WorkspaceAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="getWorkspaceFile"></a>
# **getWorkspaceFile**
> File getWorkspaceFile(organization\_id, workspace\_id, file\_name)

Download the Workspace File specified

    Download a specific file from workspace storage. Requires &#39;file_name&#39; query parameter. Returns file as binary stream. Returns error if file doesn&#39;t exist.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **file\_name** | **String**| The file name | [default to null] |

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
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |

### Return type

[**WorkspaceSecurity**](../Models/WorkspaceSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listWorkspaceFiles"></a>
# **listWorkspaceFiles**
> List listWorkspaceFiles(organization\_id, workspace\_id)

List all Workspace files

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |

### Return type

[**List**](../Models/WorkspaceFile.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listWorkspaceRolePermissions"></a>
# **listWorkspaceRolePermissions**
> List listWorkspaceRolePermissions(organization\_id, workspace\_id, role)

Get the Workspace permission by given role

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **role** | **String**| The Role | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listWorkspaceSecurityUsers"></a>
# **listWorkspaceSecurityUsers**
> List listWorkspaceSecurityUsers(organization\_id, workspace\_id)

Get the Workspace security users list

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listWorkspaces"></a>
# **listWorkspaces**
> List listWorkspaces(organization\_id, page, size)

List all Workspaces

    Retrieve a paginated list of all workspaces in an organization that the user has permission to view.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **page** | **Integer**| Page number to query (first page is at index 0) | [optional] [default to null] |
| **size** | **Integer**| Amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="updateWorkspace"></a>
# **updateWorkspace**
> Workspace updateWorkspace(organization\_id, workspace\_id, WorkspaceUpdateRequest)

Update a workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **WorkspaceUpdateRequest** | [**WorkspaceUpdateRequest**](../Models/WorkspaceUpdateRequest.md)| The new Workspace details. This endpoint can&#39;t be used to update security | |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="updateWorkspaceAccessControl"></a>
# **updateWorkspaceAccessControl**
> WorkspaceAccessControl updateWorkspaceAccessControl(organization\_id, workspace\_id, identity\_id, WorkspaceRole)

Update the specified access to User for a Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **identity\_id** | **String**| The User identifier | [default to null] |
| **WorkspaceRole** | [**WorkspaceRole**](../Models/WorkspaceRole.md)| The new Workspace Access Control | |

### Return type

[**WorkspaceAccessControl**](../Models/WorkspaceAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="updateWorkspaceDefaultSecurity"></a>
# **updateWorkspaceDefaultSecurity**
> WorkspaceSecurity updateWorkspaceDefaultSecurity(organization\_id, workspace\_id, WorkspaceRole)

Update the Workspace default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |
| **workspace\_id** | **String**| The Workspace identifier | [default to null] |
| **WorkspaceRole** | [**WorkspaceRole**](../Models/WorkspaceRole.md)| This change the workspace default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the workspace. | |

### Return type

[**WorkspaceSecurity**](../Models/WorkspaceSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

