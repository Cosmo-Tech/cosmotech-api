# ConnectorApi

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**findAllConnectors**](ConnectorApi.md#findAllConnectors) | **GET** /connectors | List all Connectors
[**findConnectorById**](ConnectorApi.md#findConnectorById) | **GET** /connectors/{connector_id} | Get the details of an connector
[**registerConnector**](ConnectorApi.md#registerConnector) | **POST** /connectors | Register a new connector
[**unregisterConnector**](ConnectorApi.md#unregisterConnector) | **DELETE** /connectors/{connector_id} | Unregister an connector
[**uploadConnector**](ConnectorApi.md#uploadConnector) | **POST** /connectors/upload | Upload and register a new connector


<a name="findAllConnectors"></a>
# **findAllConnectors**
> List findAllConnectors()

List all Connectors

### Parameters
This endpoint does not need any parameter.

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

Get the details of an connector

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **connector\_id** | **String**| the Connector identifier | [default to null]

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

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **Connector** | [**Connector**](../Models/Connector.md)| the Connector to register |

### Return type

[**Connector**](../Models/Connector.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unregisterConnector"></a>
# **unregisterConnector**
> Connector unregisterConnector(connector\_id)

Unregister an connector

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **connector\_id** | **String**| the Connector identifier | [default to null]

### Return type

[**Connector**](../Models/Connector.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="uploadConnector"></a>
# **uploadConnector**
> Connector uploadConnector(body)

Upload and register a new connector

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **body** | **File**| the Connector to upload and register |

### Return type

[**Connector**](../Models/Connector.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/yaml
- **Accept**: application/json

