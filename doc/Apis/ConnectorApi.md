# ConnectorApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**findAllConnectors**](ConnectorApi.md#findAllConnectors) | **GET** /connectors | List all Connectors |
| [**findConnectorById**](ConnectorApi.md#findConnectorById) | **GET** /connectors/{connector_id} | Get the details of a connector |
| [**registerConnector**](ConnectorApi.md#registerConnector) | **POST** /connectors | Register a new connector |
| [**unregisterConnector**](ConnectorApi.md#unregisterConnector) | **DELETE** /connectors/{connector_id} | Unregister a connector |


<a name="findAllConnectors"></a>
# **findAllConnectors**
> List findAllConnectors(page, size)

List all Connectors

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Connector.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findConnectorById"></a>
# **findConnectorById**
> Connector findConnectorById(connector\_id)

Get the details of a connector

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **connector\_id** | **String**| the Connector identifier | [default to null] |

### Return type

[**Connector**](../Models/Connector.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="registerConnector"></a>
# **registerConnector**
> Connector registerConnector(Connector)

Register a new connector

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **Connector** | [**Connector**](../Models/Connector.md)| the Connector to register | |

### Return type

[**Connector**](../Models/Connector.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="unregisterConnector"></a>
# **unregisterConnector**
> unregisterConnector(connector\_id)

Unregister a connector

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **connector\_id** | **String**| the Connector identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

