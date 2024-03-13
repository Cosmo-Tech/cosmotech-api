# DefaultApi

All URIs are relative to *https://dev.api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**linkWorkspace**](DefaultApi.md#linkWorkspace) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/link | 
[**unlinkWorkspace**](DefaultApi.md#unlinkWorkspace) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/unlink | 


<a name="linkWorkspace"></a>
# **linkWorkspace**
> Dataset linkWorkspace(organization\_id, dataset\_id, workspaceId)



### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]
 **workspaceId** | **String**| workspace id to be linked to | [default to null]

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unlinkWorkspace"></a>
# **unlinkWorkspace**
> Dataset unlinkWorkspace(organization\_id, dataset\_id, workspaceId)



### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]
 **workspaceId** | **String**| workspace id to be linked to | [default to null]

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

