# TwingraphApi

All URIs are relative to *https://dev.api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**bulkQuery**](TwingraphApi.md#bulkQuery) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/bulk_query | 
[**delete**](TwingraphApi.md#delete) | **DELETE** /organizations/{organization_id}/twingraph/{graph_id} | 
[**downloadGraph**](TwingraphApi.md#downloadGraph) | **GET** /organizations/{organization_id}/twingraph/bulk_query/download/{hash} | 
[**findAllTwingraphs**](TwingraphApi.md#findAllTwingraphs) | **GET** /organizations/{organization_id}/twingraphs | 
[**getGraphMetaData**](TwingraphApi.md#getGraphMetaData) | **GET** /organizations/{organization_id}/twingraph/{graph_id}/metadata | 
[**importGraph**](TwingraphApi.md#importGraph) | **POST** /organizations/{organization_id}/twingraph/import | 
[**jobStatus**](TwingraphApi.md#jobStatus) | **GET** /organizations/{organization_id}/job/{job_id}/status | 
[**query**](TwingraphApi.md#query) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/query | 


<a name="bulkQuery"></a>
# **bulkQuery**
> TwinGraphHash bulkQuery(organization\_id, graph\_id, TwinGraphQuery)



    Run a query on a graph instance and return the result as a zip file in async mode

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]
 **TwinGraphQuery** | [**TwinGraphQuery**](../Models/TwinGraphQuery.md)| the query to run |

### Return type

[**TwinGraphHash**](../Models/TwinGraphHash.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

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

<a name="downloadGraph"></a>
# **downloadGraph**
> File downloadGraph(organization\_id, hash)



    Download a graph compressed in a zip file

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **hash** | **String**| the Graph download identifier | [default to null]

### Return type

**File**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/octet-stream

<a name="findAllTwingraphs"></a>
# **findAllTwingraphs**
> List findAllTwingraphs(organization\_id)



    Return the list of all graphs stored in the organization

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getGraphMetaData"></a>
# **getGraphMetaData**
> Object getGraphMetaData(organization\_id, graph\_id)



    Return the metaData of the specified graph

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]

### Return type

**Object**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

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

<a name="jobStatus"></a>
# **jobStatus**
> String jobStatus(organization\_id, job\_id)



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

