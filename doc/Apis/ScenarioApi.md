# ScenarioApi

All URIs are relative to *https://api.azure.cosmo-platform.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**compareScenarios**](ScenarioApi.md#compareScenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/compare/{compared_scenario_id} | Compare the Scenario with another one and returns the difference for parameters values
[**createScenario**](ScenarioApi.md#createScenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Create a new scenario
[**deleteScenario**](ScenarioApi.md#deleteScenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Delete a scenario
[**findAllScenarios**](ScenarioApi.md#findAllScenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | List all Scenarios
[**findScenarioById**](ScenarioApi.md#findScenarioById) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Get the details of an scenario
[**getScenariosTree**](ScenarioApi.md#getScenariosTree) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/tree | Get the Scenarios Tree
[**updateScenario**](ScenarioApi.md#updateScenario) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Update a scenario


<a name="compareScenarios"></a>
# **compareScenarios**
> ScenarioComparisonResult compareScenarios(organization\_id, workspace\_id, scenario\_id, compared\_scenario\_id)

Compare the Scenario with another one and returns the difference for parameters values

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **compared\_scenario\_id** | **String**| the Scenario identifier to compare to | [default to null]

### Return type

[**ScenarioComparisonResult**](../Models/ScenarioComparisonResult.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="createScenario"></a>
# **createScenario**
> Scenario createScenario(organization\_id, workspace\_id, Scenario)

Create a new scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **Scenario** | [**Scenario**](../Models/Scenario.md)| the Scenario to create |

### Return type

[**Scenario**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="deleteScenario"></a>
# **deleteScenario**
> Scenario deleteScenario(organization\_id, workspace\_id, scenario\_id)

Delete a scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]

### Return type

[**Scenario**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findAllScenarios"></a>
# **findAllScenarios**
> List findAllScenarios(organization\_id, workspace\_id)

List all Scenarios

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]

### Return type

[**List**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findScenarioById"></a>
# **findScenarioById**
> Scenario findScenarioById(organization\_id, workspace\_id, scenario\_id)

Get the details of an scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]

### Return type

[**Scenario**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenariosTree"></a>
# **getScenariosTree**
> List getScenariosTree(organization\_id, workspace\_id)

Get the Scenarios Tree

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]

### Return type

[**List**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="updateScenario"></a>
# **updateScenario**
> Scenario updateScenario(organization\_id, workspace\_id, scenario\_id, Scenario)

Update a scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **Scenario** | [**Scenario**](../Models/Scenario.md)| the new Scenario details. |

### Return type

[**Scenario**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

