# OrganizationApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createOrganization**](OrganizationApi.md#createOrganization) | **POST** /organizations | Create a new organization |
| [**createOrganizationAccessControl**](OrganizationApi.md#createOrganizationAccessControl) | **POST** /organizations/{organization_id}/security/access | Add a control access to the Organization |
| [**deleteOrganization**](OrganizationApi.md#deleteOrganization) | **DELETE** /organizations/{organization_id} | Delete an organization |
| [**deleteOrganizationAccessControl**](OrganizationApi.md#deleteOrganizationAccessControl) | **DELETE** /organizations/{organization_id}/security/access/{identity_id} | Remove the specified access from the given Organization |
| [**getOrganization**](OrganizationApi.md#getOrganization) | **GET** /organizations/{organization_id} | Get the details of an Organization |
| [**getOrganizationAccessControl**](OrganizationApi.md#getOrganizationAccessControl) | **GET** /organizations/{organization_id}/security/access/{identity_id} | Get a control access for the Organization |
| [**getOrganizationPermissions**](OrganizationApi.md#getOrganizationPermissions) | **GET** /organizations/{organization_id}/permissions/{role} | Get the Organization permissions by given role |
| [**getOrganizationSecurity**](OrganizationApi.md#getOrganizationSecurity) | **GET** /organizations/{organization_id}/security | Get the Organization security information |
| [**listOrganizationSecurityUsers**](OrganizationApi.md#listOrganizationSecurityUsers) | **GET** /organizations/{organization_id}/security/users | Get the Organization security users list |
| [**listOrganizations**](OrganizationApi.md#listOrganizations) | **GET** /organizations | List all Organizations |
| [**listPermissions**](OrganizationApi.md#listPermissions) | **GET** /organizations/permissions | Get all permissions per components |
| [**updateOrganization**](OrganizationApi.md#updateOrganization) | **PATCH** /organizations/{organization_id} | Update an Organization |
| [**updateOrganizationAccessControl**](OrganizationApi.md#updateOrganizationAccessControl) | **PATCH** /organizations/{organization_id}/security/access/{identity_id} | Update the specified access to User for an Organization |
| [**updateOrganizationDefaultSecurity**](OrganizationApi.md#updateOrganizationDefaultSecurity) | **POST** /organizations/{organization_id}/security/default | Update the Organization default security |


<a name="createOrganization"></a>
# **createOrganization**
> Organization createOrganization(OrganizationCreateRequest)

Create a new organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **OrganizationCreateRequest** | [**OrganizationCreateRequest**](../Models/OrganizationCreateRequest.md)| the Organization to create | |

### Return type

[**Organization**](../Models/Organization.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="createOrganizationAccessControl"></a>
# **createOrganizationAccessControl**
> OrganizationAccessControl createOrganizationAccessControl(organization\_id, OrganizationAccessControl)

Add a control access to the Organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **OrganizationAccessControl** | [**OrganizationAccessControl**](../Models/OrganizationAccessControl.md)| the new Organization security access to add. | |

### Return type

[**OrganizationAccessControl**](../Models/OrganizationAccessControl.md)

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
| **organization\_id** | **String**| the Organization identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteOrganizationAccessControl"></a>
# **deleteOrganizationAccessControl**
> deleteOrganizationAccessControl(organization\_id, identity\_id)

Remove the specified access from the given Organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="getOrganization"></a>
# **getOrganization**
> Organization getOrganization(organization\_id)

Get the details of an Organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |

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

Get a control access for the Organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
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

Get the Organization permissions by given role

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
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

Get the Organization security information

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |

### Return type

[**OrganizationSecurity**](../Models/OrganizationSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listOrganizationSecurityUsers"></a>
# **listOrganizationSecurityUsers**
> List listOrganizationSecurityUsers(organization\_id)

Get the Organization security users list

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| The Organization identifier | [default to null] |

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

List all Organizations

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

<a name="listPermissions"></a>
# **listPermissions**
> List listPermissions()

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

<a name="updateOrganization"></a>
# **updateOrganization**
> Organization updateOrganization(organization\_id, OrganizationUpdateRequest)

Update an Organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **OrganizationUpdateRequest** | [**OrganizationUpdateRequest**](../Models/OrganizationUpdateRequest.md)| the new Organization details. This endpoint can&#39;t be used to update security | |

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

Update the specified access to User for an Organization

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |
| **OrganizationRole** | [**OrganizationRole**](../Models/OrganizationRole.md)| The new Organization Access Control | |

### Return type

[**OrganizationAccessControl**](../Models/OrganizationAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="updateOrganizationDefaultSecurity"></a>
# **updateOrganizationDefaultSecurity**
> OrganizationSecurity updateOrganizationDefaultSecurity(organization\_id, OrganizationRole)

Update the Organization default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **OrganizationRole** | [**OrganizationRole**](../Models/OrganizationRole.md)| This change the organization default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the organization. | |

### Return type

[**OrganizationSecurity**](../Models/OrganizationSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

