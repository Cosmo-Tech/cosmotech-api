# UserApi

All URIs are relative to *https://api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**authorizeUser**](UserApi.md#authorizeUser) | **GET** /oauth2/authorize | Authorize an User with OAuth2. Delegated to configured OAuth2 service
[**findAllUsers**](UserApi.md#findAllUsers) | **GET** /users | List all Users
[**findUserById**](UserApi.md#findUserById) | **GET** /users/{user_id} | Get the details of an user
[**getCurrentUser**](UserApi.md#getCurrentUser) | **GET** /users/me | Get the details of the logged-in User
[**getOrganizationCurrentUser**](UserApi.md#getOrganizationCurrentUser) | **GET** /organizations/{organization_id}/me | Get the details of a logged-in User with roles for an Organization
[**getWorkspaceCurrentUser**](UserApi.md#getWorkspaceCurrentUser) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/me | Get the details of the logged-in user with roles for a Workspace
[**registerUser**](UserApi.md#registerUser) | **POST** /users | Register a new user
[**testPlatform**](UserApi.md#testPlatform) | **GET** /test | test platform API call
[**unregisterUser**](UserApi.md#unregisterUser) | **DELETE** /users/{user_id} | Unregister an user
[**updateUser**](UserApi.md#updateUser) | **PATCH** /users/{user_id} | Update a User


<a name="authorizeUser"></a>
# **authorizeUser**
> authorizeUser()

Authorize an User with OAuth2. Delegated to configured OAuth2 service

### Parameters
This endpoint does not need any parameter.

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="findAllUsers"></a>
# **findAllUsers**
> List findAllUsers()

List all Users

### Parameters
This endpoint does not need any parameter.

### Return type

[**List**](../Models/User.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findUserById"></a>
# **findUserById**
> User findUserById(user\_id)

Get the details of an user

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **user\_id** | **String**| the User identifier | [default to null]

### Return type

[**User**](../Models/User.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getCurrentUser"></a>
# **getCurrentUser**
> User getCurrentUser()

Get the details of the logged-in User

### Parameters
This endpoint does not need any parameter.

### Return type

[**User**](../Models/User.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getOrganizationCurrentUser"></a>
# **getOrganizationCurrentUser**
> User getOrganizationCurrentUser(organization\_id)

Get the details of a logged-in User with roles for an Organization

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

### Return type

[**User**](../Models/User.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getWorkspaceCurrentUser"></a>
# **getWorkspaceCurrentUser**
> User getWorkspaceCurrentUser(organization\_id, workspace\_id)

Get the details of the logged-in user with roles for a Workspace

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]

### Return type

[**User**](../Models/User.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="registerUser"></a>
# **registerUser**
> User registerUser(User)

Register a new user

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **User** | [**User**](../Models/User.md)| the User to register |

### Return type

[**User**](../Models/User.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="testPlatform"></a>
# **testPlatform**
> String testPlatform()

test platform API call

### Parameters
This endpoint does not need any parameter.

### Return type

[**String**](../Models/string.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: text/plain

<a name="unregisterUser"></a>
# **unregisterUser**
> unregisterUser(user\_id)

Unregister an user

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **user\_id** | **String**| the User identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="updateUser"></a>
# **updateUser**
> User updateUser(user\_id, User)

Update a User

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **user\_id** | **String**| the User identifier | [default to null]
 **User** | [**User**](../Models/User.md)| the new User details. Organization membership is handled via the /organizations endpoint. |

### Return type

[**User**](../Models/User.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

