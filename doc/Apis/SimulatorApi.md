# SimulatorApi

All URIs are relative to *https://api.azure.cosmo-platform.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createSimulator**](SimulatorApi.md#createSimulator) | **POST** /organizations/{organization_id}/simulators | Register a new simulator
[**deleteSimulator**](SimulatorApi.md#deleteSimulator) | **DELETE** /organizations/{organization_id}/simulators/{simulator_id} | Delete a simulator
[**findAllSimulators**](SimulatorApi.md#findAllSimulators) | **GET** /organizations/{organization_id}/simulators | List all Simulators
[**findSimulatorById**](SimulatorApi.md#findSimulatorById) | **GET** /organizations/{organization_id}/simulators/{simulator_id} | Get the details of a simulator
[**updateSimulator**](SimulatorApi.md#updateSimulator) | **PATCH** /organizations/{organization_id}/simulators/{simulator_id} | Update a simulator
[**upload**](SimulatorApi.md#upload) | **POST** /organizations/{organization_id}/simulators/upload | Upload and register a new simulator


<a name="createSimulator"></a>
# **createSimulator**
> Simulator createSimulator(organization\_id, Simulator)

Register a new simulator

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **Simulator** | [**Simulator**](../Models/Simulator.md)| the Simulator to create |

### Return type

[**Simulator**](../Models/Simulator.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="deleteSimulator"></a>
# **deleteSimulator**
> Simulator deleteSimulator(organization\_id, simulator\_id)

Delete a simulator

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **simulator\_id** | **String**| the Simulator identifier | [default to null]

### Return type

[**Simulator**](../Models/Simulator.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findAllSimulators"></a>
# **findAllSimulators**
> List findAllSimulators(organization\_id)

List all Simulators

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

### Return type

[**List**](../Models/Simulator.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findSimulatorById"></a>
# **findSimulatorById**
> Simulator findSimulatorById(organization\_id, simulator\_id)

Get the details of a simulator

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **simulator\_id** | **String**| the Simulator identifier | [default to null]

### Return type

[**Simulator**](../Models/Simulator.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="updateSimulator"></a>
# **updateSimulator**
> Simulator updateSimulator(organization\_id, simulator\_id, Simulator)

Update a simulator

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **simulator\_id** | **String**| the Simulator identifier | [default to null]
 **Simulator** | [**Simulator**](../Models/Simulator.md)| the new Simulator details. |

### Return type

[**Simulator**](../Models/Simulator.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="upload"></a>
# **upload**
> Simulator upload(organization\_id, body)

Upload and register a new simulator

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **body** | **File**| the Simulator to upload and register |

### Return type

[**Simulator**](../Models/Simulator.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/yaml
- **Accept**: application/json

