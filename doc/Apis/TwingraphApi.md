# TwingraphApi

All URIs are relative to *https://dev.api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**delete**](TwingraphApi.md#delete) | **DELETE** /organizations/{organization_id}/twingraph/{graph_id} | 
[**importGraph**](TwingraphApi.md#importGraph) | **POST** /organizations/{organization_id}/twingraph/import | 
[**importJobStatus**](TwingraphApi.md#importJobStatus) | **GET** /organizations/{organization_id}/twingraph/{job_id}/status | 
[**query**](TwingraphApi.md#query) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/query | 


<a name="delete"></a>
# **delete**
> delete(organization\_id, graph\_id)



    Launch a mass delete job

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="importGraph"></a>
# **importGraph**
> TwinGraphImportInfo importGraph(organization\_id, TwinGraphImport)



    Import a new version of a twin graph

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **TwinGraphImport** | [**TwinGraphImport**](../Models/TwinGraphImport.md)| the graph to import |

### Return type

[**TwinGraphImportInfo**](../Models/TwinGraphImportInfo.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="importJobStatus"></a>
# **importJobStatus**
> String importJobStatus(organization\_id, job\_id)



    Get the status of a job

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **job\_id** | **String**| the job identifier | [default to null]

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/yaml, application/json

<a name="query"></a>
# **query**
> String query(organization\_id, graph\_id, TwinGraphQuery)



    Run a query on a graph instance

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]
 **TwinGraphQuery** | [**TwinGraphQuery**](../Models/TwinGraphQuery.md)| the query to run |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

