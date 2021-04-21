# OrganizationApi

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**findAllOrganizations**](OrganizationApi.md#findAllOrganizations) | **GET** /organizations | List all Organizations
[**findOrganizationById**](OrganizationApi.md#findOrganizationById) | **GET** /organizations/{organization_id} | Get the details of an organization
[**registerOrganization**](OrganizationApi.md#registerOrganization) | **POST** /organizations | Register a new organization
[**unregisterOrganization**](OrganizationApi.md#unregisterOrganization) | **DELETE** /organizations/{organization_id} | Unregister an organization
[**updateOrganization**](OrganizationApi.md#updateOrganization) | **PATCH** /organizations/{organization_id} | Update an organization


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

Get the details of an organization

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

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unregisterOrganization"></a>
# **unregisterOrganization**
> Organization unregisterOrganization(organization\_id)

Unregister an organization

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

<a name="updateOrganization"></a>
# **updateOrganization**
> Organization updateOrganization(organization\_id, Organization)

Update an organization

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

- **Content-Type**: application/json
- **Accept**: application/json

