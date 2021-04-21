# DatasetApi

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**copyDataset**](DatasetApi.md#copyDataset) | **POST** /organizations/{organization_id}/datasets/copy | Copy a Dataset to another Dataset. Source must have a read capable connector and Target a write capable connector.
[**createDataset**](DatasetApi.md#createDataset) | **POST** /organizations/{organization_id}/datasets | Create a new dataset
[**deleteDataset**](DatasetApi.md#deleteDataset) | **DELETE** /organizations/{organization_id}/datasets/{dataset_id} | Delete a dataset
[**findAllDatasets**](DatasetApi.md#findAllDatasets) | **GET** /organizations/{organization_id}/datasets | List all Datasets
[**findDatasetById**](DatasetApi.md#findDatasetById) | **GET** /organizations/{organization_id}/datasets/{dataset_id} | Get the details of a dataset
[**updateDataset**](DatasetApi.md#updateDataset) | **PATCH** /organizations/{organization_id}/datasets/{dataset_id} | Update a dataset


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

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createDataset"></a>
# **createDataset**
> Dataset createDataset(organization\_id, Dataset)

Create a new dataset

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

- **Content-Type**: application/json
- **Accept**: application/json

<a name="deleteDataset"></a>
# **deleteDataset**
> Dataset deleteDataset(organization\_id, dataset\_id)

Delete a dataset

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

<a name="findAllDatasets"></a>
# **findAllDatasets**
> List findAllDatasets(organization\_id)

List all Datasets

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

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

Get the details of a dataset

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

- **Content-Type**: application/json
- **Accept**: application/json

