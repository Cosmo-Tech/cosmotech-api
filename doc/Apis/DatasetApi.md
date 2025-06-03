# DatasetApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createDataset**](DatasetApi.md#createDataset) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets | Create a Dataset |
| [**createDatasetAccessControl**](DatasetApi.md#createDatasetAccessControl) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access | Add a control access to the Dataset |
| [**createDatasetPart**](DatasetApi.md#createDatasetPart) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts | Create a data part of a Dataset |
| [**deleteDataset**](DatasetApi.md#deleteDataset) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id} | Delete a Dataset |
| [**deleteDatasetAccessControl**](DatasetApi.md#deleteDatasetAccessControl) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id} | Remove the specified access from the given Dataset |
| [**deleteDatasetPart**](DatasetApi.md#deleteDatasetPart) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id} | Delete a Dataset part |
| [**downloadDatasetPart**](DatasetApi.md#downloadDatasetPart) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id}/download | Download data from a dataset part |
| [**getDataset**](DatasetApi.md#getDataset) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id} | Retrieve a Dataset |
| [**getDatasetAccessControl**](DatasetApi.md#getDatasetAccessControl) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id} | Get a control access for the Dataset |
| [**getDatasetPart**](DatasetApi.md#getDatasetPart) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id} | Retrieve a data part of a Dataset |
| [**listDatasetParts**](DatasetApi.md#listDatasetParts) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts | Retrieve all dataset parts of a Dataset |
| [**listDatasetSecurityUsers**](DatasetApi.md#listDatasetSecurityUsers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/users | Get the Dataset security users list |
| [**listDatasets**](DatasetApi.md#listDatasets) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets | Retrieve a list of defined Dataset |
| [**queryData**](DatasetApi.md#queryData) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id}/query | Get data of a Dataset |
| [**replaceDatasetPart**](DatasetApi.md#replaceDatasetPart) | **PUT** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id} | Replace existing dataset parts of a Dataset |
| [**updateDataset**](DatasetApi.md#updateDataset) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id} | Update a Dataset |
| [**updateDatasetAccessControl**](DatasetApi.md#updateDatasetAccessControl) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id} | Update the specified access to User for a Dataset |
| [**updateDatasetDefaultSecurity**](DatasetApi.md#updateDatasetDefaultSecurity) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/default | Set the Dataset default security |


<a name="createDataset"></a>
# **createDataset**
> Dataset createDataset(organization\_id, workspace\_id, datasetCreateRequest, files)

Create a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **datasetCreateRequest** | [**DatasetCreateRequest**](../Models/DatasetCreateRequest.md)|  | [default to null] |
| **files** | **List**| Notes:   - Each parts defined in dataset should have a file defined in this list   - Please ensure that upload files order match with data parts list defined     - First file uploaded will match with first dataset parts and so on  | [optional] [default to null] |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: multipart/form-data
- **Accept**: application/json, application/yaml

<a name="createDatasetAccessControl"></a>
# **createDatasetAccessControl**
> DatasetAccessControl createDatasetAccessControl(organization\_id, workspace\_id, dataset\_id, DatasetAccessControl)

Add a control access to the Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **DatasetAccessControl** | [**DatasetAccessControl**](../Models/DatasetAccessControl.md)| the new Dataset security access to add. | |

### Return type

[**DatasetAccessControl**](../Models/DatasetAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="createDatasetPart"></a>
# **createDatasetPart**
> DatasetPart createDatasetPart(organization\_id, workspace\_id, dataset\_id, datasetPartCreateRequest, file)

Create a data part of a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **datasetPartCreateRequest** | [**DatasetPartCreateRequest**](../Models/DatasetPartCreateRequest.md)|  | [default to null] |
| **file** | **File**| Data file to upload | [optional] [default to null] |

### Return type

[**DatasetPart**](../Models/DatasetPart.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: multipart/form-data
- **Accept**: application/json, application/yaml

<a name="deleteDataset"></a>
# **deleteDataset**
> deleteDataset(organization\_id, workspace\_id, dataset\_id)

Delete a Dataset

    Delete a dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteDatasetAccessControl"></a>
# **deleteDatasetAccessControl**
> deleteDatasetAccessControl(organization\_id, workspace\_id, dataset\_id, identity\_id)

Remove the specified access from the given Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteDatasetPart"></a>
# **deleteDatasetPart**
> deleteDatasetPart(organization\_id, workspace\_id, dataset\_id, dataset\_part\_id)

Delete a Dataset part

    Delete a dataset part

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **dataset\_part\_id** | **String**| the Dataset part identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="downloadDatasetPart"></a>
# **downloadDatasetPart**
> File downloadDatasetPart(organization\_id, workspace\_id, dataset\_id, dataset\_part\_id)

Download data from a dataset part

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **dataset\_part\_id** | **String**| the Dataset part identifier | [default to null] |

### Return type

**File**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/octet-stream

<a name="getDataset"></a>
# **getDataset**
> Dataset getDataset(organization\_id, workspace\_id, dataset\_id)

Retrieve a Dataset

    Retrieve a dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

### Return type

[**Dataset**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="getDatasetAccessControl"></a>
# **getDatasetAccessControl**
> DatasetAccessControl getDatasetAccessControl(organization\_id, workspace\_id, dataset\_id, identity\_id)

Get a control access for the Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

[**DatasetAccessControl**](../Models/DatasetAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="getDatasetPart"></a>
# **getDatasetPart**
> DatasetPart getDatasetPart(organization\_id, workspace\_id, dataset\_id, dataset\_part\_id)

Retrieve a data part of a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **dataset\_part\_id** | **String**| the Dataset part identifier | [default to null] |

### Return type

[**DatasetPart**](../Models/DatasetPart.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listDatasetParts"></a>
# **listDatasetParts**
> List listDatasetParts(organization\_id, workspace\_id, dataset\_id, page, size)

Retrieve all dataset parts of a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **page** | **Integer**| Page number to query (first page is at index 0) | [optional] [default to null] |
| **size** | **Integer**| Amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/DatasetPart.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listDatasetSecurityUsers"></a>
# **listDatasetSecurityUsers**
> List listDatasetSecurityUsers(organization\_id, workspace\_id, dataset\_id)

Get the Dataset security users list

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listDatasets"></a>
# **listDatasets**
> List listDatasets(organization\_id, workspace\_id, page, size)

Retrieve a list of defined Dataset

    List all datasets

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **page** | **Integer**| Page number to query (first page is at index 0) | [optional] [default to null] |
| **size** | **Integer**| Amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="queryData"></a>
# **queryData**
> List queryData(organization\_id, workspace\_id, dataset\_id, dataset\_part\_id, filters, sums, counts, offset, limit)

Get data of a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **dataset\_part\_id** | **String**| the DatasetPart identifier | [default to null] |
| **filters** | [**List**](../Models/String.md)| Property names that should be part of the response data. You can specify a property name like:  - id  - stock  - quantity  - ...  | [optional] [default to null] |
| **sums** | [**List**](../Models/String.md)| Property names to sum by | [optional] [default to null] |
| **counts** | [**List**](../Models/String.md)| Property names to count by | [optional] [default to null] |
| **offset** | **Integer**| The query offset | [optional] [default to null] |
| **limit** | **Integer**| The query limit | [optional] [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="replaceDatasetPart"></a>
# **replaceDatasetPart**
> DatasetPart replaceDatasetPart(organization\_id, workspace\_id, dataset\_id, dataset\_part\_id, file, datasetPartUpdateRequest)

Replace existing dataset parts of a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **dataset\_part\_id** | **String**| the Dataset part identifier | [default to null] |
| **file** | **File**| Data file to upload | [optional] [default to null] |
| **datasetPartUpdateRequest** | [**DatasetPartUpdateRequest**](../Models/DatasetPartUpdateRequest.md)|  | [optional] [default to null] |

### Return type

[**DatasetPart**](../Models/DatasetPart.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: multipart/form-data
- **Accept**: application/json, application/yaml

<a name="updateDataset"></a>
# **updateDataset**
> List updateDataset(organization\_id, workspace\_id, dataset\_id, files, dataset)

Update a Dataset

    Update a dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **files** | **List**| Notes:   - Each parts defined in dataset should have a file defined in this list   - Please ensure that upload files order match with data parts list defined     - First file uploaded will match with first dataset parts and so on  | [optional] [default to null] |
| **dataset** | [**DatasetUpdateRequest**](../Models/DatasetUpdateRequest.md)|  | [optional] [default to null] |

### Return type

[**List**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: multipart/form-data
- **Accept**: application/json, application/yaml

<a name="updateDatasetAccessControl"></a>
# **updateDatasetAccessControl**
> DatasetAccessControl updateDatasetAccessControl(organization\_id, workspace\_id, dataset\_id, identity\_id, DatasetRole)

Update the specified access to User for a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |
| **DatasetRole** | [**DatasetRole**](../Models/DatasetRole.md)| The new Dataset Access Control | |

### Return type

[**DatasetAccessControl**](../Models/DatasetAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="updateDatasetDefaultSecurity"></a>
# **updateDatasetDefaultSecurity**
> DatasetSecurity updateDatasetDefaultSecurity(organization\_id, workspace\_id, dataset\_id, DatasetRole)

Set the Dataset default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **dataset\_id** | **String**| the Dataset identifier | [default to null] |
| **DatasetRole** | [**DatasetRole**](../Models/DatasetRole.md)| This change the dataset default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the dataset. | |

### Return type

[**DatasetSecurity**](../Models/DatasetSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

