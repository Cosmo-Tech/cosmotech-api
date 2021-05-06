# WorkspaceApi

All URIs are relative to *https://api.azure.cosmo-platform.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addUsersToOrganizationWorkspace**](WorkspaceApi.md#addUsersToOrganizationWorkspace) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/users | Add (or replace) users to the Workspace specified
[**createWorkspace**](WorkspaceApi.md#createWorkspace) | **POST** /organizations/{organization_id}/workspaces | Create a new workspace
[**deleteWorkspace**](WorkspaceApi.md#deleteWorkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id} | Delete a workspace
[**deleteWorkspaceFile**](WorkspaceApi.md#deleteWorkspaceFile) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/files | Delete a workspace file
[**findAllWorkspaceFiles**](WorkspaceApi.md#findAllWorkspaceFiles) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/files | List all Workspace files
[**findAllWorkspaces**](WorkspaceApi.md#findAllWorkspaces) | **GET** /organizations/{organization_id}/workspaces | List all Workspaces
[**findWorkspaceById**](WorkspaceApi.md#findWorkspaceById) | **GET** /organizations/{organization_id}/workspaces/{workspace_id} | Get the details of an workspace
[**removeAllUsersOfWorkspace**](WorkspaceApi.md#removeAllUsersOfWorkspace) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/users | Remove all users from the Workspace specified
[**updateWorkspace**](WorkspaceApi.md#updateWorkspace) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id} | Update a workspace
[**uploadWorkspaceFile**](WorkspaceApi.md#uploadWorkspaceFile) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/files | Upload a file for the Workspace


<a name="addUsersToOrganizationWorkspace"></a>
# **addUsersToOrganizationWorkspace**
> List addUsersToOrganizationWorkspace(organization\_id, workspace\_id, WorkspaceUser)

Add (or replace) users to the Workspace specified

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **WorkspaceUser** | [**List**](../Models/WorkspaceUser.md)| the Users to add. Any User with the same ID is overwritten |

### Return type

[**List**](../Models/WorkspaceUser.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createWorkspace"></a>
# **createWorkspace**
> Workspace createWorkspace(organization\_id, Workspace)

Create a new workspace

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **Workspace** | [**Workspace**](../Models/Workspace.md)| the Workspace to create |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="deleteWorkspace"></a>
# **deleteWorkspace**
> Workspace deleteWorkspace(organization\_id, workspace\_id)

Delete a workspace

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="deleteWorkspaceFile"></a>
# **deleteWorkspaceFile**
> WorkspaceFile deleteWorkspaceFile(organization\_id, workspace\_id, WorkspaceFile)

Delete a workspace file

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **WorkspaceFile** | [**WorkspaceFile**](../Models/WorkspaceFile.md)| the file to upload |

### Return type

[**WorkspaceFile**](../Models/WorkspaceFile.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="findAllWorkspaceFiles"></a>
# **findAllWorkspaceFiles**
> List findAllWorkspaceFiles(organization\_id, workspace\_id)

List all Workspace files

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]

### Return type

[**List**](../Models/WorkspaceFile.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findAllWorkspaces"></a>
# **findAllWorkspaces**
> List findAllWorkspaces(organization\_id)

List all Workspaces

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

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

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="removeAllUsersOfWorkspace"></a>
# **removeAllUsersOfWorkspace**
> removeAllUsersOfWorkspace(organization\_id, workspace\_id)

Remove all users from the Workspace specified

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="updateWorkspace"></a>
# **updateWorkspace**
> Workspace updateWorkspace(organization\_id, workspace\_id, Workspace)

Update a workspace

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **Workspace** | [**Workspace**](../Models/Workspace.md)| the new Workspace details. |

### Return type

[**Workspace**](../Models/Workspace.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="uploadWorkspaceFile"></a>
# **uploadWorkspaceFile**
> WorkspaceFile uploadWorkspaceFile(organization\_id, workspace\_id, fileName)

Upload a file for the Workspace

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **fileName** | **File**|  | [optional] [default to null]

### Return type

[**WorkspaceFile**](../Models/WorkspaceFile.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: multipart/form-data
- **Accept**: application/json

