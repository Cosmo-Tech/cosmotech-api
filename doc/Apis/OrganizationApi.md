# OrganizationApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addOrganizationAccessControl**](OrganizationApi.md#addOrganizationAccessControl) | **POST** /organizations/{organization_id}/security/access | Add a control access to the organization |
| [**createOrganization**](OrganizationApi.md#createOrganization) | **POST** /organizations | Create a new organization |
| [**deleteOrganization**](OrganizationApi.md#deleteOrganization) | **DELETE** /organizations/{organization_id} | Delete an organization |
| [**getAllPermissions**](OrganizationApi.md#getAllPermissions) | **GET** /organizations/permissions | Get all permissions per components |
| [**getOrganization**](OrganizationApi.md#getOrganization) | **GET** /organizations/{organization_id} | Get the details of an organization |
| [**getOrganizationAccessControl**](OrganizationApi.md#getOrganizationAccessControl) | **GET** /organizations/{organization_id}/security/access/{identity_id} | Get a control access for the organization |
| [**getOrganizationPermissions**](OrganizationApi.md#getOrganizationPermissions) | **GET** /organizations/{organization_id}/permissions/{role} | Get the organization permissions by given role |
| [**getOrganizationSecurity**](OrganizationApi.md#getOrganizationSecurity) | **GET** /organizations/{organization_id}/security | Get the organization security information |
| [**getOrganizationSecurityUsers**](OrganizationApi.md#getOrganizationSecurityUsers) | **GET** /organizations/{organization_id}/security/users | Get the organization security users list |
| [**listOrganizations**](OrganizationApi.md#listOrganizations) | **GET** /organizations | List all organizations |
| [**removeOrganizationAccessControl**](OrganizationApi.md#removeOrganizationAccessControl) | **DELETE** /organizations/{organization_id}/security/access/{identity_id} | Remove the specified access from the given organization |
| [**setOrganizationDefaultSecurity**](OrganizationApi.md#setOrganizationDefaultSecurity) | **POST** /organizations/{organization_id}/security/default | Set the organization default security |
| [**updateOrganization**](OrganizationApi.md#updateOrganization) | **PATCH** /organizations/{organization_id} | Update an organization |
| [**updateOrganizationAccessControl**](OrganizationApi.md#updateOrganizationAccessControl) | **PATCH** /organizations/{organization_id}/security/access/{identity_id} | Update the specified access to User for an organization |


<a name="addOrganizationAccessControl"></a>
# **addOrganizationAccessControl**
> OrganizationAccessControl addOrganizationAccessControl(organization\_id, OrganizationAccessControl)

Add a control access to the organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |
| **OrganizationAccessControl** | [**OrganizationAccessControl**](../Models/OrganizationAccessControl.md)| the new organization security access to add. | |

### Return type

[**OrganizationAccessControl**](../Models/OrganizationAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="createOrganization"></a>
# **createOrganization**
> Organization createOrganization(OrganizationRequest)

Create a new organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **OrganizationRequest** | [**OrganizationRequest**](../Models/OrganizationRequest.md)| the organization to create | |

### Return type

[**Organization**](../Models/Organization.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="deleteOrganization"></a>
# **deleteOrganization**
> deleteOrganization(organization\_id)

Delete an organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="getAllPermissions"></a>
# **getAllPermissions**
> List getAllPermissions()

Get all permissions per components

### Parameters
This endpoint does not need any parameter.

### Return type

[**List**](../Models/ComponentRolePermissions.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getOrganization"></a>
# **getOrganization**
> Organization getOrganization(organization\_id)

Get the details of an organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |

### Return type

[**Organization**](../Models/Organization.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getOrganizationAccessControl"></a>
# **getOrganizationAccessControl**
> OrganizationAccessControl getOrganizationAccessControl(organization\_id, identity\_id)

Get a control access for the organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

[**OrganizationAccessControl**](../Models/OrganizationAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getOrganizationPermissions"></a>
# **getOrganizationPermissions**
> List getOrganizationPermissions(organization\_id, role)

Get the organization permissions by given role

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |
| **role** | **String**| the Role | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getOrganizationSecurity"></a>
# **getOrganizationSecurity**
> OrganizationSecurity getOrganizationSecurity(organization\_id)

Get the organization security information

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |

### Return type

[**OrganizationSecurity**](../Models/OrganizationSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getOrganizationSecurityUsers"></a>
# **getOrganizationSecurityUsers**
> List getOrganizationSecurityUsers(organization\_id)

Get the organization security users list

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The organization identifier | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listOrganizations"></a>
# **listOrganizations**
> List listOrganizations(page, size)

List all organizations

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **page** | **Integer**| page number to query (first page is at index 0) | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Organization.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="removeOrganizationAccessControl"></a>
# **removeOrganizationAccessControl**
> removeOrganizationAccessControl(organization\_id, identity\_id)

Remove the specified access from the given organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="setOrganizationDefaultSecurity"></a>
# **setOrganizationDefaultSecurity**
> OrganizationSecurity setOrganizationDefaultSecurity(organization\_id, OrganizationRole)

Set the organization default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |
| **OrganizationRole** | [**OrganizationRole**](../Models/OrganizationRole.md)| This change the organization default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the organization. | |

### Return type

[**OrganizationSecurity**](../Models/OrganizationSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateOrganization"></a>
# **updateOrganization**
> Organization updateOrganization(organization\_id, OrganizationUpdate)

Update an organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |
| **OrganizationUpdate** | [**OrganizationUpdate**](../Models/OrganizationUpdate.md)| the new organization details. This endpoint can&#39;t be used to update security | |

### Return type

[**Organization**](../Models/Organization.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateOrganizationAccessControl"></a>
# **updateOrganizationAccessControl**
> OrganizationAccessControl updateOrganizationAccessControl(organization\_id, identity\_id, OrganizationRole)

Update the specified access to User for an organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the organization identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |
| **OrganizationRole** | [**OrganizationRole**](../Models/OrganizationRole.md)| The new organization Access Control | |

### Return type

[**OrganizationAccessControl**](../Models/OrganizationAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

