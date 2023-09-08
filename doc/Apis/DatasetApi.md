# DatasetApi

All URIs are relative to *https://dev.api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addOrReplaceDatasetCompatibilityElements**](DatasetApi.md#addOrReplaceDatasetCompatibilityElements) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Add Dataset Compatibility elements.
[**copyDataset**](DatasetApi.md#copyDataset) | **POST** /organizations/{organization_id}/datasets/copy | Copy a Dataset to another Dataset. Source must have a read capable connector and Target a write capable connector.
[**createDataset**](DatasetApi.md#createDataset) | **POST** /organizations/{organization_id}/datasets | Create a new Dataset
[**createSubDataset**](DatasetApi.md#createSubDataset) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/subdataset | Run a query on a dataset
[**createTwingraphEntities**](DatasetApi.md#createTwingraphEntities) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Create new entities in a graph instance
[**deleteDataset**](DatasetApi.md#deleteDataset) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id} | Delete a dataset
[**deleteTwingraphEntities**](DatasetApi.md#deleteTwingraphEntities) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Delete entities in a graph instance
[**findAllDatasets**](DatasetApi.md#findAllDatasets) | **GET** /organizations/{organization_id}/datasets | List all Datasets
[**findDatasetById**](DatasetApi.md#findDatasetById) | **GET** /organizations/{organization_id}/datasets/{dataset_id} | Get the details of a Dataset
[**getDatasetTwingraphStatus**](DatasetApi.md#getDatasetTwingraphStatus) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/job/{job_id}/status | Get the status of twingraph import
[**getTwingraphEntities**](DatasetApi.md#getTwingraphEntities) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Get entities in a graph instance
[**importDataset**](DatasetApi.md#importDataset) | **POST** /organizations/{organization_id}/datasets/import | Import a new Dataset
[**refreshDataset**](DatasetApi.md#refreshDataset) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/refresh | Refresh dataset
[**removeAllDatasetCompatibilityElements**](DatasetApi.md#removeAllDatasetCompatibilityElements) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Remove all Dataset Compatibility elements from the Dataset specified
[**searchDatasets**](DatasetApi.md#searchDatasets) | **POST** /organizations/{organization_id}/datasets/search | Search Datasets
[**twingraphBatchUpdate**](DatasetApi.md#twingraphBatchUpdate) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/batch | Async batch update by loading a CSV file on a graph instance 
[**twingraphQuery**](DatasetApi.md#twingraphQuery) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/twingraph | Run a query on a graph instance and return the result as a json
[**updateDataset**](DatasetApi.md#updateDataset) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id} | Update a dataset
[**updateTwingraphEntities**](DatasetApi.md#updateTwingraphEntities) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Update entities in a graph instance
[**uploadTwingraph**](DatasetApi.md#uploadTwingraph) | **POST** /organizations/{organization_id}/datasets/{dataset_id} | Upload Twingraph with ZIP File


<a name="addOrReplaceDatasetCompatibilityElements"></a>
# **addOrReplaceDatasetCompatibilityElements**
> List addOrReplaceDatasetCompatibilityElements(organization\_id, dataset\_id, DatasetCompatibility)

Add Dataset Compatibility elements.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]
 **DatasetCompatibility** | [**List**](../Models/DatasetCompatibility.md)| the Dataset Compatibility elements |

### Return type

[**List**](../Models/DatasetCompatibility.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="copyDataset"></a>
# **copyDataset**
> DatasetCopyParameters copyDataset(organization\_id, DatasetCopyParameters)

Copy a Dataset to another Dataset. Source must have a read capable connector and Target a write capable connector.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **DatasetCopyParameters** | [**DatasetCopyParameters**](../Models/DatasetCopyParameters.md)| the Dataset copy parameters |

### Return type

[**DatasetCopyParameters**](../Models/DatasetCopyParameters.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="createDataset"></a>
# **createDataset**
> Dataset createDataset(organization\_id, Dataset)

Create a new Dataset

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **Dataset** | [**Dataset**](../Models/Dataset.md)| the Dataset to create |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="createSubDataset"></a>
# **createSubDataset**
> Dataset createSubDataset(organization\_id, dataset\_id, SubDatasetGraphQuery)

Run a query on a dataset

    Run a query on a dataset

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]
 **SubDatasetGraphQuery** | [**SubDatasetGraphQuery**](../Models/SubDatasetGraphQuery.md)| the query to run |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createTwingraphEntities"></a>
# **createTwingraphEntities**
> String createTwingraphEntities(organization\_id, dataset\_id, type, GraphProperties)

Create new entities in a graph instance

    create new entities in a graph instance

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset Identifier | [default to null]
 **type** | **String**| the entity model type | [default to null] [enum: node, relationship]
 **GraphProperties** | [**List**](../Models/GraphProperties.md)| the entities to create |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="deleteDataset"></a>
# **deleteDataset**
> deleteDataset(organization\_id, dataset\_id)

Delete a dataset

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteTwingraphEntities"></a>
# **deleteTwingraphEntities**
> deleteTwingraphEntities(organization\_id, dataset\_id, type, ids)

Delete entities in a graph instance

    delete entities in a graph instance

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset Identifier | [default to null]
 **type** | **String**| the entity model type | [default to null] [enum: node, relationship]
 **ids** | [**List**](../Models/String.md)| the entities to delete | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="findAllDatasets"></a>
# **findAllDatasets**
> List findAllDatasets(organization\_id, page, size)

List all Datasets

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **page** | **Integer**| page number to query | [optional] [default to null]
 **size** | **Integer**| amount of result by page | [optional] [default to null]

### Return type

[**List**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findDatasetById"></a>
# **findDatasetById**
> Dataset findDatasetById(organization\_id, dataset\_id)

Get the details of a Dataset

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getDatasetTwingraphStatus"></a>
# **getDatasetTwingraphStatus**
> String getDatasetTwingraphStatus(organization\_id, dataset\_id, job\_id)

Get the status of twingraph import

    Get the status of a twingraph import

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the dataset identifier | [default to null]
 **job\_id** | **String**| the job identifier | [default to null]

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/yaml, application/json

<a name="getTwingraphEntities"></a>
# **getTwingraphEntities**
> String getTwingraphEntities(organization\_id, dataset\_id, type, ids)

Get entities in a graph instance

    get entities in a graph instance

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset Identifier | [default to null]
 **type** | **String**| the entity model type | [default to null] [enum: node, relationship]
 **ids** | [**List**](../Models/String.md)| the entities to get | [default to null]

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="importDataset"></a>
# **importDataset**
> Dataset importDataset(organization\_id, Dataset)

Import a new Dataset

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **Dataset** | [**Dataset**](../Models/Dataset.md)| the Dataset to import |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="refreshDataset"></a>
# **refreshDataset**
> DatasetTwinGraphInfo refreshDataset(organization\_id, dataset\_id)

Refresh dataset

    Refresh ADT, Storage dataset

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]

### Return type

[**DatasetTwinGraphInfo**](../Models/DatasetTwinGraphInfo.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="removeAllDatasetCompatibilityElements"></a>
# **removeAllDatasetCompatibilityElements**
> removeAllDatasetCompatibilityElements(organization\_id, dataset\_id)

Remove all Dataset Compatibility elements from the Dataset specified

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="searchDatasets"></a>
# **searchDatasets**
> List searchDatasets(organization\_id, DatasetSearch, page, size)

Search Datasets

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **DatasetSearch** | [**DatasetSearch**](../Models/DatasetSearch.md)| the Dataset search parameters |
 **page** | **Integer**| page number to query | [optional] [default to null]
 **size** | **Integer**| amount of result by page | [optional] [default to null]

### Return type

[**List**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="twingraphBatchUpdate"></a>
# **twingraphBatchUpdate**
> TwinGraphBatchResult twingraphBatchUpdate(organization\_id, dataset\_id, twinGraphQuery, body)

Async batch update by loading a CSV file on a graph instance 

    Async batch update by loading a CSV file on a graph instance 

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset Identifier | [default to null]
 **twinGraphQuery** | [**DatasetTwinGraphQuery**](../Models/.md)|  | [default to null]
 **body** | **File**|  |

### Return type

[**TwinGraphBatchResult**](../Models/TwinGraphBatchResult.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: text/csv, application/octet-stream
- **Accept**: application/json

<a name="twingraphQuery"></a>
# **twingraphQuery**
> String twingraphQuery(organization\_id, dataset\_id, DatasetTwinGraphQuery)

Run a query on a graph instance and return the result as a json

    Run a query on a graph instance and return the result as a json

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]
 **DatasetTwinGraphQuery** | [**DatasetTwinGraphQuery**](../Models/DatasetTwinGraphQuery.md)| the query to run |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="updateDataset"></a>
# **updateDataset**
> Dataset updateDataset(organization\_id, dataset\_id, Dataset)

Update a dataset

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]
 **Dataset** | [**Dataset**](../Models/Dataset.md)| the new Dataset details. |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateTwingraphEntities"></a>
# **updateTwingraphEntities**
> String updateTwingraphEntities(organization\_id, dataset\_id, type, GraphProperties)

Update entities in a graph instance

    update entities in a graph instance

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset Identifier | [default to null]
 **type** | **String**| the entity model type | [default to null] [enum: node, relationship]
 **GraphProperties** | [**List**](../Models/GraphProperties.md)| the entities to update |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="uploadTwingraph"></a>
# **uploadTwingraph**
> uploadTwingraph(organization\_id, dataset\_id, body)

Upload Twingraph with ZIP File

    Upload Twingraph ZIP

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **dataset\_id** | **String**| the Dataset identifier | [default to null]
 **body** | **File**|  |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/octet-stream
- **Accept**: Not defined

