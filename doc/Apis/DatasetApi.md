# DatasetApi

All URIs are relative to *https://dev.api.cosmotech.com*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addDatasetAccessControl**](DatasetApi.md#addDatasetAccessControl) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/security/access | Add a control access to the Dataset |
| [**addOrReplaceDatasetCompatibilityElements**](DatasetApi.md#addOrReplaceDatasetCompatibilityElements) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Add Dataset Compatibility elements. |
| [**copyDataset**](DatasetApi.md#copyDataset) | **POST** /organizations/{organization_id}/datasets/copy | Copy a Dataset to another Dataset. |
| [**createDataset**](DatasetApi.md#createDataset) | **POST** /organizations/{organization_id}/datasets | Create a new Dataset |
| [**createSubDataset**](DatasetApi.md#createSubDataset) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/subdataset | Create a sub-dataset from the dataset in parameter |
| [**createTwingraphEntities**](DatasetApi.md#createTwingraphEntities) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Create new entities in a graph instance |
| [**deleteDataset**](DatasetApi.md#deleteDataset) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id} | Delete a dataset |
| [**deleteTwingraphEntities**](DatasetApi.md#deleteTwingraphEntities) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Delete entities in a graph instance |
| [**downloadTwingraph**](DatasetApi.md#downloadTwingraph) | **GET** /organizations/{organization_id}/datasets/twingraph/download/{hash} | Download a graph as a zip file |
| [**findAllDatasets**](DatasetApi.md#findAllDatasets) | **GET** /organizations/{organization_id}/datasets | List all Datasets |
| [**findDatasetById**](DatasetApi.md#findDatasetById) | **GET** /organizations/{organization_id}/datasets/{dataset_id} | Get the details of a Dataset |
| [**getAllData**](DatasetApi.md#getAllData) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/data | Get the data of a Dataset |
| [**getDataInfo**](DatasetApi.md#getDataInfo) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/info | Get the data information of a Dataset |
| [**getDatasetAccessControl**](DatasetApi.md#getDatasetAccessControl) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/security/access/{identity_id} | Get a control access for the Dataset |
| [**getDatasetSecurity**](DatasetApi.md#getDatasetSecurity) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/security | Get the Dataset security information |
| [**getDatasetSecurityUsers**](DatasetApi.md#getDatasetSecurityUsers) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/security/users | Get the Dataset security users list |
| [**getDatasetTwingraphStatus**](DatasetApi.md#getDatasetTwingraphStatus) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/status | Get the dataset&#39;s refresh job status |
| [**getTwingraphEntities**](DatasetApi.md#getTwingraphEntities) | **GET** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Get entities in a graph instance |
| [**linkWorkspace**](DatasetApi.md#linkWorkspace) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/link |  |
| [**refreshDataset**](DatasetApi.md#refreshDataset) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/refresh | Refresh data on dataset from dataset&#39;s source |
| [**removeAllDatasetCompatibilityElements**](DatasetApi.md#removeAllDatasetCompatibilityElements) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/compatibility | Remove all Dataset Compatibility elements from the Dataset specified |
| [**removeDatasetAccessControl**](DatasetApi.md#removeDatasetAccessControl) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id}/security/access/{identity_id} | Remove the specified access from the given Dataset |
| [**rollbackRefresh**](DatasetApi.md#rollbackRefresh) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/refresh/rollback | Rollback the dataset after a failed refresh |
| [**searchDatasets**](DatasetApi.md#searchDatasets) | **POST** /organizations/{organization_id}/datasets/search | Search Datasets by tags |
| [**setDatasetDefaultSecurity**](DatasetApi.md#setDatasetDefaultSecurity) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/security/default | Set the Dataset default security |
| [**twingraphBatchQuery**](DatasetApi.md#twingraphBatchQuery) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/batch-query | Run a query on a graph instance and return the result as a zip file in async mode |
| [**twingraphBatchUpdate**](DatasetApi.md#twingraphBatchUpdate) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/batch | Async batch update by loading a CSV file on a graph instance  |
| [**twingraphQuery**](DatasetApi.md#twingraphQuery) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/twingraph | Return the result of a query made on the graph instance as a json |
| [**unlinkWorkspace**](DatasetApi.md#unlinkWorkspace) | **POST** /organizations/{organization_id}/datasets/{dataset_id}/unlink |  |
| [**updateDataset**](DatasetApi.md#updateDataset) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id} | Update a dataset |
| [**updateDatasetAccessControl**](DatasetApi.md#updateDatasetAccessControl) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id}/security/access/{identity_id} | Update the specified access to User for a Dataset |
| [**updateTwingraphEntities**](DatasetApi.md#updateTwingraphEntities) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id}/twingraph/{type} | Update entities in a graph instance |
| [**uploadTwingraph**](DatasetApi.md#uploadTwingraph) | **POST** /organizations/{organization_id}/datasets/{dataset_id} | Upload data from zip file to dataset&#39;s twingraph |


<a name="addDatasetAccessControl"></a>
# **addDatasetAccessControl**
> DatasetAccessControl addDatasetAccessControl(organization\_id, dataset\_id, DatasetAccessControl)

Add a control access to the Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **DatasetAccessControl** | [**DatasetAccessControl**](../Models/DatasetAccessControl.md)| the new Dataset security access to add. | |

### Return type

[**DatasetAccessControl**](../Models/DatasetAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="addOrReplaceDatasetCompatibilityElements"></a>
# **addOrReplaceDatasetCompatibilityElements**
> List addOrReplaceDatasetCompatibilityElements(organization\_id, dataset\_id, DatasetCompatibility)

Add Dataset Compatibility elements.

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **DatasetCompatibility** | [**List**](../Models/DatasetCompatibility.md)| the Dataset Compatibility elements | |

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

Copy a Dataset to another Dataset.

    Not implemented!

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **DatasetCopyParameters** | [**DatasetCopyParameters**](../Models/DatasetCopyParameters.md)| the Dataset copy parameters | |

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

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **Dataset** | [**Dataset**](../Models/Dataset.md)| the Dataset to create | |

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

Create a sub-dataset from the dataset in parameter

    Create a copy of the dataset using the results of the list of queries given in parameter. Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **SubDatasetGraphQuery** | [**SubDatasetGraphQuery**](../Models/SubDatasetGraphQuery.md)| the Cypher query to filter | |

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

    Create new entities in a graph instance Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset Identifier | [default to null] |
| **type** | **String**| the entity model type | [default to null] [enum: node, relationship] |
| **GraphProperties** | [**List**](../Models/GraphProperties.md)| the entities to create | |

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

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

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

    Delete entities in a graph instance Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset Identifier | [default to null] |
| **type** | **String**| the entity model type | [default to null] [enum: node, relationship] |
| **ids** | [**List**](../Models/String.md)| the entities to delete | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="downloadTwingraph"></a>
# **downloadTwingraph**
> File downloadTwingraph(organization\_id, hash)

Download a graph as a zip file

    Download the compressed graph reference by the hash in a zip file Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **hash** | **String**| the Graph download identifier | [default to null] |

### Return type

**File**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/octet-stream

<a name="findAllDatasets"></a>
# **findAllDatasets**
> List findAllDatasets(organization\_id, page, size)

List all Datasets

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

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

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getAllData"></a>
# **getAllData**
> List getAllData(organization\_id, dataset\_id, name)

Get the data of a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **name** | **String**| name of a dedicated entity | [optional] [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getDataInfo"></a>
# **getDataInfo**
> Map getDataInfo(organization\_id, dataset\_id)

Get the data information of a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

### Return type

[**Map**](../Models/AnyType.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getDatasetAccessControl"></a>
# **getDatasetAccessControl**
> DatasetAccessControl getDatasetAccessControl(organization\_id, dataset\_id, identity\_id)

Get a control access for the Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

[**DatasetAccessControl**](../Models/DatasetAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getDatasetSecurity"></a>
# **getDatasetSecurity**
> DatasetSecurity getDatasetSecurity(organization\_id, dataset\_id)

Get the Dataset security information

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

### Return type

[**DatasetSecurity**](../Models/DatasetSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getDatasetSecurityUsers"></a>
# **getDatasetSecurityUsers**
> List getDatasetSecurityUsers(organization\_id, dataset\_id)

Get the Dataset security users list

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getDatasetTwingraphStatus"></a>
# **getDatasetTwingraphStatus**
> String getDatasetTwingraphStatus(organization\_id, dataset\_id)

Get the dataset&#39;s refresh job status

    Get the status of the import workflow lauch on the dataset&#39;s refresh. This endpoint needs to be called to update a dataset IngestionStatus or TwincacheStatus

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the dataset identifier | [default to null] |

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

    Get entities in a graph instance Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset Identifier | [default to null] |
| **type** | **String**| the entity model type | [default to null] [enum: node, relationship] |
| **ids** | [**List**](../Models/String.md)| the entities to get | [default to null] |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="linkWorkspace"></a>
# **linkWorkspace**
> Dataset linkWorkspace(organization\_id, dataset\_id, workspaceId)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **workspaceId** | **String**| workspace id to be linked to | [default to null] |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="refreshDataset"></a>
# **refreshDataset**
> DatasetTwinGraphInfo refreshDataset(organization\_id, dataset\_id)

Refresh data on dataset from dataset&#39;s source

    Refresh dataset from parent source. At date, sources can be:      dataset (refresh from another dataset)      Azure Digital twin       Azure storage      Local File (import a new file)  During refresh, datas are overwritten Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

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

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="removeDatasetAccessControl"></a>
# **removeDatasetAccessControl**
> removeDatasetAccessControl(organization\_id, dataset\_id, identity\_id)

Remove the specified access from the given Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="rollbackRefresh"></a>
# **rollbackRefresh**
> String rollbackRefresh(organization\_id, dataset\_id)

Rollback the dataset after a failed refresh

    Rollback the twingraph on a dataset after a failed refresh Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="searchDatasets"></a>
# **searchDatasets**
> List searchDatasets(organization\_id, DatasetSearch, page, size)

Search Datasets by tags

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **DatasetSearch** | [**DatasetSearch**](../Models/DatasetSearch.md)| the Dataset search parameters | |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="setDatasetDefaultSecurity"></a>
# **setDatasetDefaultSecurity**
> DatasetSecurity setDatasetDefaultSecurity(organization\_id, dataset\_id, DatasetRole)

Set the Dataset default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **DatasetRole** | [**DatasetRole**](../Models/DatasetRole.md)| This change the dataset default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the dataset. | |

### Return type

[**DatasetSecurity**](../Models/DatasetSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="twingraphBatchQuery"></a>
# **twingraphBatchQuery**
> DatasetTwinGraphHash twingraphBatchQuery(organization\_id, dataset\_id, DatasetTwinGraphQuery)

Run a query on a graph instance and return the result as a zip file in async mode

    Run a query on a graph instance and return the result as a zip file in async mode Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Graph Identifier | [default to null] |
| **DatasetTwinGraphQuery** | [**DatasetTwinGraphQuery**](../Models/DatasetTwinGraphQuery.md)| the query to run | |

### Return type

[**DatasetTwinGraphHash**](../Models/DatasetTwinGraphHash.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="twingraphBatchUpdate"></a>
# **twingraphBatchUpdate**
> TwinGraphBatchResult twingraphBatchUpdate(organization\_id, dataset\_id, twinGraphQuery, body)

Async batch update by loading a CSV file on a graph instance 

    Async batch update by loading a CSV file on a graph instance  Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset Identifier | [default to null] |
| **twinGraphQuery** | [**DatasetTwinGraphQuery**](../Models/.md)|  | [default to null] |
| **body** | **File**|  | |

### Return type

[**TwinGraphBatchResult**](../Models/TwinGraphBatchResult.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: text/csv, application/octet-stream
- **Accept**: application/json

<a name="twingraphQuery"></a>
# **twingraphQuery**
> List twingraphQuery(organization\_id, dataset\_id, DatasetTwinGraphQuery)

Return the result of a query made on the graph instance as a json

    Run a query on a graph instance and return the result as a json Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **DatasetTwinGraphQuery** | [**DatasetTwinGraphQuery**](../Models/DatasetTwinGraphQuery.md)| the query to run | |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlinkWorkspace"></a>
# **unlinkWorkspace**
> Dataset unlinkWorkspace(organization\_id, dataset\_id, workspaceId)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **workspaceId** | **String**| workspace id to be linked to | [default to null] |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="updateDataset"></a>
# **updateDataset**
> Dataset updateDataset(organization\_id, dataset\_id, Dataset)

Update a dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **Dataset** | [**Dataset**](../Models/Dataset.md)| the new Dataset details. This endpoint can&#39;t be used to update security | |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateDatasetAccessControl"></a>
# **updateDatasetAccessControl**
> DatasetAccessControl updateDatasetAccessControl(organization\_id, dataset\_id, identity\_id, DatasetRole)

Update the specified access to User for a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |
| **DatasetRole** | [**DatasetRole**](../Models/DatasetRole.md)| The new Dataset Access Control | |

### Return type

[**DatasetAccessControl**](../Models/DatasetAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="updateTwingraphEntities"></a>
# **updateTwingraphEntities**
> String updateTwingraphEntities(organization\_id, dataset\_id, type, GraphProperties)

Update entities in a graph instance

    update entities in a graph instance

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset Identifier | [default to null] |
| **type** | **String**| the entity model type | [default to null] [enum: node, relationship] |
| **GraphProperties** | [**List**](../Models/GraphProperties.md)| The entities to update Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true  | |

### Return type

**String**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="uploadTwingraph"></a>
# **uploadTwingraph**
> FileUploadValidation uploadTwingraph(organization\_id, dataset\_id, body)

Upload data from zip file to dataset&#39;s twingraph

    To create a new graph from flat files,  you need to create a Zip file. This Zip file must countain two folders named Edges and Nodes.  .zip hierarchy: *main_folder/Nodes *main_folder/Edges  In each folder you can place one or multiple csv files containing your Nodes or Edges data.  Your csv files must follow the following header (column name) requirements:  The Nodes CSVs requires at least one column (the 1st).Column name &#x3D; &#39;id&#39;. It will represent the nodes ID Ids must be populated with string  The Edges CSVs require three columns named, in order, * source * target * id  those colomns represent * The source of the edge * The target of the edge * The id of the edge  All following columns content are up to you. Note: This endpoint is activated only if &#x60;csm.platform.twincache.useGraphModule&#x60; property is set to true 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **body** | **File**|  | |

### Return type

[**FileUploadValidation**](../Models/FileUploadValidation.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/octet-stream
- **Accept**: application/json

