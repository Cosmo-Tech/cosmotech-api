# DatasetApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addDatasetAccessControl**](DatasetApi.md#addDatasetAccessControl) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access | Add a control access to the Dataset |
| [**createDataset**](DatasetApi.md#createDataset) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets | Create a Dataset |
| [**deleteDataset**](DatasetApi.md#deleteDataset) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id} | Delete a Dataset |
| [**getDataset**](DatasetApi.md#getDataset) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id} | Retrieve a Dataset |
| [**getDatasetAccessControl**](DatasetApi.md#getDatasetAccessControl) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id} | Get a control access for the Dataset |
| [**getDatasetSecurityUsers**](DatasetApi.md#getDatasetSecurityUsers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/users | Get the Dataset security users list |
| [**listDatasets**](DatasetApi.md#listDatasets) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/datasets | Retrieve a list of defined Dataset |
| [**removeDatasetAccessControl**](DatasetApi.md#removeDatasetAccessControl) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id} | Remove the specified access from the given Dataset |
| [**setDatasetDefaultSecurity**](DatasetApi.md#setDatasetDefaultSecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/default | Set the Dataset default security |
| [**updateDataset**](DatasetApi.md#updateDataset) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id} | Update a Dataset |
| [**updateDatasetAccessControl**](DatasetApi.md#updateDatasetAccessControl) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id} | Update the specified access to User for a Dataset |


<a name="addDatasetAccessControl"></a>
# **addDatasetAccessControl**
> DatasetAccessControl addDatasetAccessControl(organization\_id, workspace\_id, dataset\_id, DatasetAccessControl)

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

<a name="createDataset"></a>
# **createDataset**
> Dataset createDataset(organization\_id, workspace\_id, files, dataset)

Create a Dataset

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **files** | **List**| Notes:   - Each parts defined in dataset should have a file defined in this list   - Please ensure that upload files order match with data parts list defined     - First file uploaded will match with first dataset parts and so on  | [optional] [default to null] |
| **dataset** | [**DatasetCreateRequest**](../Models/DatasetCreateRequest.md)|  | [optional] [default to null] |

### Return type

[**Dataset**](../Models/Dataset.md)

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

<a name="getDatasetSecurityUsers"></a>
# **getDatasetSecurityUsers**
> List getDatasetSecurityUsers(organization\_id, workspace\_id, dataset\_id)

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
> List listDatasets(organization\_id, workspace\_id)

Retrieve a list of defined Dataset

    List all datasets

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |

### Return type

[**List**](../Models/Dataset.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="removeDatasetAccessControl"></a>
# **removeDatasetAccessControl**
> removeDatasetAccessControl(organization\_id, workspace\_id, dataset\_id, identity\_id)

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

<a name="setDatasetDefaultSecurity"></a>
# **setDatasetDefaultSecurity**
> DatasetSecurity setDatasetDefaultSecurity(organization\_id, workspace\_id, dataset\_id, DatasetRole)

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

