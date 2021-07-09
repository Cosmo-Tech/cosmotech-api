# OrganizationApi

All URIs are relative to *https://api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addOrReplaceUsersInOrganization**](OrganizationApi.md#addOrReplaceUsersInOrganization) | **POST** /organizations/{organization_id}/users | Add (or replace) users in the Organization specified
[**findAllOrganizations**](OrganizationApi.md#findAllOrganizations) | **GET** /organizations | List all Organizations
[**findOrganizationById**](OrganizationApi.md#findOrganizationById) | **GET** /organizations/{organization_id} | Get the details of an Organization
[**registerOrganization**](OrganizationApi.md#registerOrganization) | **POST** /organizations | Register a new organization
[**removeAllUsersInOrganization**](OrganizationApi.md#removeAllUsersInOrganization) | **DELETE** /organizations/{organization_id}/users | Remove all users from the Organization specified
[**removeUserFromOrganization**](OrganizationApi.md#removeUserFromOrganization) | **DELETE** /organizations/{organization_id}/users/{user_id} | Remove the specified user from the given Organization
[**unregisterOrganization**](OrganizationApi.md#unregisterOrganization) | **DELETE** /organizations/{organization_id} | Unregister an organization
[**updateOrganization**](OrganizationApi.md#updateOrganization) | **PATCH** /organizations/{organization_id} | Update an Organization
[**updateSolutionsContainerRegistryByOrganizationId**](OrganizationApi.md#updateSolutionsContainerRegistryByOrganizationId) | **PATCH** /organizations/{organization_id}/services/solutionsContainerRegistry | Update the solutions container registry configuration for the Organization specified
[**updateStorageByOrganizationId**](OrganizationApi.md#updateStorageByOrganizationId) | **PATCH** /organizations/{organization_id}/services/storage | Update storage configuration for the Organization specified
[**updateTenantCredentialsByOrganizationId**](OrganizationApi.md#updateTenantCredentialsByOrganizationId) | **PATCH** /organizations/{organization_id}/services/tenantCredentials | Update tenant credentials for the Organization specified


<a name="addOrReplaceUsersInOrganization"></a>
# **addOrReplaceUsersInOrganization**
> List addOrReplaceUsersInOrganization(organization\_id, OrganizationUser)

Add (or replace) users in the Organization specified

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **OrganizationUser** | [**List**](../Models/OrganizationUser.md)| the Users to add. Any User with the same ID is overwritten |

### Return type

[**List**](../Models/OrganizationUser.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="findAllOrganizations"></a>
# **findAllOrganizations**
> List findAllOrganizations()

List all Organizations

### Parameters
This endpoint does not need any parameter.

### Return type

[**List**](../Models/Organization.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findOrganizationById"></a>
# **findOrganizationById**
> Organization findOrganizationById(organization\_id)

Get the details of an Organization

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

### Return type

[**Organization**](../Models/Organization.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="registerOrganization"></a>
# **registerOrganization**
> Organization registerOrganization(Organization)

Register a new organization

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **Organization** | [**Organization**](../Models/Organization.md)| the Organization to register |

### Return type

[**Organization**](../Models/Organization.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="removeAllUsersInOrganization"></a>
# **removeAllUsersInOrganization**
> removeAllUsersInOrganization(organization\_id)

Remove all users from the Organization specified

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="removeUserFromOrganization"></a>
# **removeUserFromOrganization**
> removeUserFromOrganization(organization\_id, user\_id)

Remove the specified user from the given Organization

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **user\_id** | **String**| the User identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="unregisterOrganization"></a>
# **unregisterOrganization**
> unregisterOrganization(organization\_id)

Unregister an organization

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="updateOrganization"></a>
# **updateOrganization**
> Organization updateOrganization(organization\_id, Organization)

Update an Organization

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **Organization** | [**Organization**](../Models/Organization.md)| the new Organization details |

### Return type

[**Organization**](../Models/Organization.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateSolutionsContainerRegistryByOrganizationId"></a>
# **updateSolutionsContainerRegistryByOrganizationId**
> OrganizationService updateSolutionsContainerRegistryByOrganizationId(organization\_id, OrganizationService)

Update the solutions container registry configuration for the Organization specified

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **OrganizationService** | [**OrganizationService**](../Models/OrganizationService.md)| the new solutions container registry configuration to use |

### Return type

[**OrganizationService**](../Models/OrganizationService.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateStorageByOrganizationId"></a>
# **updateStorageByOrganizationId**
> OrganizationService updateStorageByOrganizationId(organization\_id, OrganizationService)

Update storage configuration for the Organization specified

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **OrganizationService** | [**OrganizationService**](../Models/OrganizationService.md)| the new Storage configuration to use |

### Return type

[**OrganizationService**](../Models/OrganizationService.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateTenantCredentialsByOrganizationId"></a>
# **updateTenantCredentialsByOrganizationId**
> Map updateTenantCredentialsByOrganizationId(organization\_id, request\_body)

Update tenant credentials for the Organization specified

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **request\_body** | [**Map**](../Models/object.md)| the new Tenant Credentials to use |

### Return type

[**Map**](../Models/object.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

