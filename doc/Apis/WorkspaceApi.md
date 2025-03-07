# WorkspaceApi

All URIs are relative to *https://dev.api.cosmotech.com*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addWorkspaceAccessControl**](WorkspaceApi.md#addWorkspaceAccessControl) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/access | Add a control access to the Workspace |
| [**createSecret**](WorkspaceApi.md#createSecret) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/secret | Create a secret for the Workspace |
| [**createWorkspace**](WorkspaceApi.md#createWorkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace |
| [**deleteAllWorkspaceFiles**](WorkspaceApi.md#deleteAllWorkspaceFiles) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files | Delete all Workspace files |
| [**deleteWorkspace**](WorkspaceApi.md#deleteWorkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace |
| [**deleteWorkspaceFile**](WorkspaceApi.md#deleteWorkspaceFile) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files/delete | Delete a workspace file |
| [**downloadWorkspaceFile**](WorkspaceApi.md#downloadWorkspaceFile) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files/download | Download the Workspace File specified |
| [**findAllWorkspaceFiles**](WorkspaceApi.md#findAllWorkspaceFiles) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files | List all Workspace files |
| [**findAllWorkspaces**](WorkspaceApi.md#findAllWorkspaces) | **GET** /organizations/{organization_id}/workspaces | List all Workspaces |
| [**findWorkspaceById**](WorkspaceApi.md#findWorkspaceById) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of an workspace |
| [**getWorkspaceAccessControl**](WorkspaceApi.md#getWorkspaceAccessControl) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Get a control access for the Workspace |
| [**getWorkspacePermissions**](WorkspaceApi.md#getWorkspacePermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/permissions/{role} | Get the Workspace permission by given role |
| [**getWorkspaceSecurity**](WorkspaceApi.md#getWorkspaceSecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security | Get the Workspace security information |
| [**getWorkspaceSecurityUsers**](WorkspaceApi.md#getWorkspaceSecurityUsers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/security/users | Get the Workspace security users list |
| [**linkDataset**](WorkspaceApi.md#linkDataset) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/link |  |
| [**removeWorkspaceAccessControl**](WorkspaceApi.md#removeWorkspaceAccessControl) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Remove the specified access from the given Organization Workspace |
| [**setWorkspaceDefaultSecurity**](WorkspaceApi.md#setWorkspaceDefaultSecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/security/default | Set the Workspace default security |
| [**unlinkDataset**](WorkspaceApi.md#unlinkDataset) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/unlink |  |
| [**updateWorkspace**](WorkspaceApi.md#updateWorkspace) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id} | Update a workspace |
| [**updateWorkspaceAccessControl**](WorkspaceApi.md#updateWorkspaceAccessControl) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id} | Update the specified access to User for a Workspace |
| [**uploadWorkspaceFile**](WorkspaceApi.md#uploadWorkspaceFile) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/files | Upload a file for the Workspace |


<a name="addWorkspaceAccessControl"></a>
# **addWorkspaceAccessControl**
> WorkspaceAccessControl addWorkspaceAccessControl(organization\_id, workspace\_id, WorkspaceAccessControl)

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

<a name="createSecret"></a>
# **createSecret**
> createSecret(organization\_id, workspace\_id, WorkspaceSecret)

Create a secret for the Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **WorkspaceSecret** | [**WorkspaceSecret**](../Models/WorkspaceSecret.md)| the definition of the secret | |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: Not defined

<a name="createWorkspace"></a>
# **createWorkspace**
> Workspace createWorkspace(organization\_id, Workspace)

Create a new workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **Workspace** | [**Workspace**](../Models/Workspace.md)| the Workspace to create | |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="deleteAllWorkspaceFiles"></a>
# **deleteAllWorkspaceFiles**
> deleteAllWorkspaceFiles(organization\_id, workspace\_id)

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

<a name="downloadWorkspaceFile"></a>
# **downloadWorkspaceFile**
> File downloadWorkspaceFile(organization\_id, workspace\_id, file\_name)

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

<a name="findAllWorkspaceFiles"></a>
# **findAllWorkspaceFiles**
> List findAllWorkspaceFiles(organization\_id, workspace\_id)

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

<a name="findAllWorkspaces"></a>
# **findAllWorkspaces**
> List findAllWorkspaces(organization\_id, page, size)

List all Workspaces

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findWorkspaceById"></a>
# **findWorkspaceById**
> Workspace findWorkspaceById(organization\_id, workspace\_id)

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

<a name="getWorkspacePermissions"></a>
# **getWorkspacePermissions**
> List getWorkspacePermissions(organization\_id, workspace\_id, role)

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

<a name="getWorkspaceSecurityUsers"></a>
# **getWorkspaceSecurityUsers**
> List getWorkspaceSecurityUsers(organization\_id, workspace\_id)

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

<a name="linkDataset"></a>
# **linkDataset**
> Workspace linkDataset(organization\_id, workspace\_id, datasetId)



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

<a name="removeWorkspaceAccessControl"></a>
# **removeWorkspaceAccessControl**
> removeWorkspaceAccessControl(organization\_id, workspace\_id, identity\_id)

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

<a name="setWorkspaceDefaultSecurity"></a>
# **setWorkspaceDefaultSecurity**
> WorkspaceSecurity setWorkspaceDefaultSecurity(organization\_id, workspace\_id, WorkspaceRole)

Set the Workspace default security

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

<a name="unlinkDataset"></a>
# **unlinkDataset**
> Workspace unlinkDataset(organization\_id, workspace\_id, datasetId)



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

<a name="updateWorkspace"></a>
# **updateWorkspace**
> Workspace updateWorkspace(organization\_id, workspace\_id, Workspace)

Update a workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **Workspace** | [**Workspace**](../Models/Workspace.md)| The new Workspace details. This endpoint can&#39;t be used to update :   - id   - ownerId   - security  | |

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

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="uploadWorkspaceFile"></a>
# **uploadWorkspaceFile**
> WorkspaceFile uploadWorkspaceFile(organization\_id, workspace\_id, file, overwrite, destination)

Upload a file for the Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **file** | **File**|  | [default to null] |
| **overwrite** | **Boolean**|  | [optional] [default to false] |
| **destination** | **String**| Destination path. Must end with a &#39;/&#39; if specifying a folder. Note that paths may or may not start with a &#39;/&#39;, but they are always treated as relative to the Workspace root location.  | [optional] [default to null] |

### Return type

[**WorkspaceFile**](../Models/WorkspaceFile.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: multipart/form-data
- **Accept**: application/json

