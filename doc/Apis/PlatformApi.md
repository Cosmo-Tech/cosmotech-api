# PlatformApi

All URIs are relative to *https://api.azure.cosmo-platform.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createPlatform**](PlatformApi.md#createPlatform) | **POST** /platform | Create a new platform
[**getPlatform**](PlatformApi.md#getPlatform) | **GET** /platform | Get the details of the platform
[**updatePlatform**](PlatformApi.md#updatePlatform) | **PATCH** /platform | Update a platform


<a name="createPlatform"></a>
# **createPlatform**
> Platform createPlatform(Platform)

Create a new platform

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **Platform** | [**Platform**](../Models/Platform.md)| the Platform to create |

### Return type

[**Platform**](../Models/Platform.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getPlatform"></a>
# **getPlatform**
> Platform getPlatform()

Get the details of the platform

### Parameters
This endpoint does not need any parameter.

### Return type

[**Platform**](../Models/Platform.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="updatePlatform"></a>
# **updatePlatform**
> Platform updatePlatform(Platform)

Update a platform

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **Platform** | [**Platform**](../Models/Platform.md)| the new Platform details. |

### Return type

[**Platform**](../Models/Platform.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

