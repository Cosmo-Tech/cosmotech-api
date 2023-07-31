# TwingraphApi

All URIs are relative to *https://dev.api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**batchQuery**](TwingraphApi.md#batchQuery) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/batch-query | Run a query on a graph instance and return the result as a zip file in async mode
[**batchUploadUpdate**](TwingraphApi.md#batchUploadUpdate) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/batch | Async batch update by loading a CSV file on a graph instance 
[**createEntities**](TwingraphApi.md#createEntities) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Create new entities in a graph instance
[**createGraph**](TwingraphApi.md#createGraph) | **POST** /organizations/{organization_id}/twingraph/{graph_id} | Create a new graph
[**delete**](TwingraphApi.md#delete) | **DELETE** /organizations/{organization_id}/twingraph/{graph_id} | Delete all versions of a graph and his metadatas
[**deleteEntities**](TwingraphApi.md#deleteEntities) | **DELETE** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Delete entities in a graph instance
[**downloadGraph**](TwingraphApi.md#downloadGraph) | **GET** /organizations/{organization_id}/twingraph/download/{hash} | Download a graph compressed in a zip file
[**findAllTwingraphs**](TwingraphApi.md#findAllTwingraphs) | **GET** /organizations/{organization_id}/twingraphs | Return the list of all graphs stored in the organization
[**getEntities**](TwingraphApi.md#getEntities) | **GET** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Get entities in a graph instance
[**getGraphMetaData**](TwingraphApi.md#getGraphMetaData) | **GET** /organizations/{organization_id}/twingraph/{graph_id}/metadata | Return the metaData of the specified graph
[**importGraph**](TwingraphApi.md#importGraph) | **POST** /organizations/{organization_id}/twingraph/import | Import a new version of a twin graph
[**jobStatus**](TwingraphApi.md#jobStatus) | **GET** /organizations/{organization_id}/job/{job_id}/status | Get the status of a job
[**query**](TwingraphApi.md#query) | **POST** /organizations/{organization_id}/twingraph/{graph_id}/query | Run a query on a graph instance
[**updateEntities**](TwingraphApi.md#updateEntities) | **PATCH** /organizations/{organization_id}/twingraph/{graph_id}/entity/{type} | Update entities in a graph instance
[**updateGraphMetaData**](TwingraphApi.md#updateGraphMetaData) | **PATCH** /organizations/{organization_id}/twingraph/{graph_id}/metadata | Update the metaData of the specified graph


<a name="batchQuery"></a>
# **batchQuery**
> TwinGraphHash batchQuery(organization\_id, graph\_id, TwinGraphQuery)

Run a query on a graph instance and return the result as a zip file in async mode

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

<a name="batchUploadUpdate"></a>
# **batchUploadUpdate**
> TwinGraphBatchResult batchUploadUpdate(organization\_id, graph\_id, twinGraphQuery, body)

Async batch update by loading a CSV file on a graph instance 

    Async batch update by loading a CSV file on a graph instance 

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]
 **twinGraphQuery** | [**TwinGraphQuery**](../Models/.md)|  | [default to null]
 **body** | **File**|  |

### Return type

[**TwinGraphBatchResult**](../Models/TwinGraphBatchResult.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: text/csv, application/octet-stream
- **Accept**: application/json

<a name="createEntities"></a>
# **createEntities**
> String createEntities(organization\_id, graph\_id, type, GraphProperties)

Create new entities in a graph instance

    create new entities in a graph instance

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]
 **type** | **String**| the entity model type | [default to null] [enum: node, relationship]
 **GraphProperties** | [**List**](../Models/GraphProperties.md)| the entities to create |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createGraph"></a>
# **createGraph**
> createGraph(organization\_id, graph\_id, body)

Create a new graph

    To create a new graph from flat files,  you need to create a Zip file. This Zip file must countain two folders named Edges and Nodes.  .zip hierarchy: *main_folder/Nodes *main_folder/Edges  In each folder you can place one or multiple csv files containing your Nodes or Edges data.  Your csv files must follow the following header (column name) requirements:  The Nodes CSVs requires at least one column (the 1st).Column name &#x3D; &#39;id&#39;. It will represent the nodes ID Ids must be populated with string  The Edges CSVs require three columns named, in order, * source * target * id  those colomns represent * The source of the edge * The target of the edge * The id of the edge  All following columns content are up to you. 

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]
 **body** | **File**|  | [optional]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/octet-stream
- **Accept**: Not defined

<a name="delete"></a>
# **delete**
> delete(organization\_id, graph\_id)

Delete all versions of a graph and his metadatas

    Delete all versions of a graph and his metadatas

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

<a name="deleteEntities"></a>
# **deleteEntities**
> deleteEntities(organization\_id, graph\_id, type, ids)

Delete entities in a graph instance

    delete entities in a graph instance

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]
 **type** | **String**| the entity model type | [default to null] [enum: node, relationship]
 **ids** | [**List**](../Models/String.md)| the entities to delete | [default to null]

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

<a name="getEntities"></a>
# **getEntities**
> String getEntities(organization\_id, graph\_id, type, ids)

Get entities in a graph instance

    get entities in a graph instance

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]
 **type** | **String**| the entity model type | [default to null] [enum: node, relationship]
 **ids** | [**List**](../Models/String.md)| the entities to get | [default to null]

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getGraphMetaData"></a>
# **getGraphMetaData**
> Object getGraphMetaData(organization\_id, graph\_id)

Return the metaData of the specified graph

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

<a name="updateEntities"></a>
# **updateEntities**
> String updateEntities(organization\_id, graph\_id, type, GraphProperties)

Update entities in a graph instance

    update entities in a graph instance

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]
 **type** | **String**| the entity model type | [default to null] [enum: node, relationship]
 **GraphProperties** | [**List**](../Models/GraphProperties.md)| the entities to update |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="updateGraphMetaData"></a>
# **updateGraphMetaData**
> Object updateGraphMetaData(organization\_id, graph\_id, request\_body)

Update the metaData of the specified graph

    Update the metaData of the specified graph

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **graph\_id** | **String**| the Graph Identifier | [default to null]
 **request\_body** | [**Map**](../Models/string.md)| the metaData to update |

### Return type

**Object**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

