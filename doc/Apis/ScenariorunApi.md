# ScenariorunApi

All URIs are relative to *https://api.azure.cosmo-platform.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**deleteScenarioRun**](ScenariorunApi.md#deleteScenarioRun) | **DELETE** /organizations/{organization_id}/scenarioruns/{scenariorun_id} | Delete a scenariorun
[**findScenarioRunById**](ScenariorunApi.md#findScenarioRunById) | **GET** /organizations/{organization_id}/scenarioruns/{scenariorun_id} | Get the details of a scenariorun
[**getCumulatedLogs**](ScenariorunApi.md#getCumulatedLogs) | **POST** /organizations/{organization_id}/scenarioruns/{scenariorun_id}/cumulatedlogs | Get the cumulated logs of a scenariorun
[**getScenarioRun**](ScenariorunApi.md#getScenarioRun) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns/{scenariorun_id} | get the ScenarioRun for the Scenario
[**getScenarioRunLogs**](ScenariorunApi.md#getScenarioRunLogs) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns/{scenariorun_id}/logs | get the logs for the ScenarioRun
[**getScenarioRuns**](ScenariorunApi.md#getScenarioRuns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns | get the list of ScenarioRuns for the Scenario
[**getWorkspaceScenarioRuns**](ScenariorunApi.md#getWorkspaceScenarioRuns) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarioruns | get the list of ScenarioRuns for the Workspace
[**runScenario**](ScenariorunApi.md#runScenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/run | run a ScenarioRun for the Scenario
[**searchScenarioRuns**](ScenariorunApi.md#searchScenarioRuns) | **POST** /organizations/{organization_id}/scenarioruns/search | Search ScenarioRuns
[**startScenarioRunContainers**](ScenariorunApi.md#startScenarioRunContainers) | **POST** /organizations/{organization_id}/scenarioruns/startcontainers | Start a new scenariorun with raw containers definition
[**startScenarioRunScenario**](ScenariorunApi.md#startScenarioRunScenario) | **POST** /organizations/{organization_id}/scenarioruns/start | Start a new scenariorun for a Scenario
[**startScenarioRunSolution**](ScenariorunApi.md#startScenarioRunSolution) | **POST** /organizations/{organization_id}/scenarioruns/startsolution | Start a new scenariorun for a Solution Run Template


<a name="deleteScenarioRun"></a>
# **deleteScenarioRun**
> deleteScenarioRun(organization\_id, scenariorun\_id)

Delete a scenariorun

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="findScenarioRunById"></a>
# **findScenarioRunById**
> ScenarioRun findScenarioRunById(organization\_id, scenariorun\_id)

Get the details of a scenariorun

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]

### Return type

[**ScenarioRun**](../Models/ScenarioRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getCumulatedLogs"></a>
# **getCumulatedLogs**
> String getCumulatedLogs(organization\_id, scenariorun\_id)

Get the cumulated logs of a scenariorun

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]

### Return type

[**String**](../Models/string.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: text/plain

<a name="getScenarioRun"></a>
# **getScenarioRun**
> ScenarioRun getScenarioRun(organization\_id, workspace\_id, scenario\_id, scenariorun\_id)

get the ScenarioRun for the Scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]

### Return type

[**ScenarioRun**](../Models/ScenarioRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioRunLogs"></a>
# **getScenarioRunLogs**
> ScenarioRunLogs getScenarioRunLogs(organization\_id, workspace\_id, scenario\_id, scenariorun\_id)

get the logs for the ScenarioRun

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]

### Return type

[**ScenarioRunLogs**](../Models/ScenarioRunLogs.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioRuns"></a>
# **getScenarioRuns**
> List getScenarioRuns(organization\_id, workspace\_id, scenario\_id)

get the list of ScenarioRuns for the Scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]

### Return type

[**List**](../Models/ScenarioRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getWorkspaceScenarioRuns"></a>
# **getWorkspaceScenarioRuns**
> List getWorkspaceScenarioRuns(organization\_id, workspace\_id)

get the list of ScenarioRuns for the Workspace

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]

### Return type

[**List**](../Models/ScenarioRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="runScenario"></a>
# **runScenario**
> ScenarioRun runScenario(organization\_id, workspace\_id, scenario\_id)

run a ScenarioRun for the Scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]

### Return type

[**ScenarioRun**](../Models/ScenarioRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="searchScenarioRuns"></a>
# **searchScenarioRuns**
> List searchScenarioRuns(organization\_id, ScenarioRunSearch)

Search ScenarioRuns

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **ScenarioRunSearch** | [**ScenarioRunSearch**](../Models/ScenarioRunSearch.md)| the ScenarioRun search parameters |

### Return type

[**List**](../Models/ScenarioRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="startScenarioRunContainers"></a>
# **startScenarioRunContainers**
> ScenarioRun startScenarioRunContainers(organization\_id, ScenarioRunStartContainers)

Start a new scenariorun with raw containers definition

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **ScenarioRunStartContainers** | [**ScenarioRunStartContainers**](../Models/ScenarioRunStartContainers.md)| the raw containers definition |

### Return type

[**ScenarioRun**](../Models/ScenarioRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="startScenarioRunScenario"></a>
# **startScenarioRunScenario**
> ScenarioRun startScenarioRunScenario(organization\_id, ScenarioRunStart)

Start a new scenariorun for a Scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **ScenarioRunStart** | [**ScenarioRunStart**](../Models/ScenarioRunStart.md)| the Scenario information to start |

### Return type

[**ScenarioRun**](../Models/ScenarioRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="startScenarioRunSolution"></a>
# **startScenarioRunSolution**
> ScenarioRun startScenarioRunSolution(organization\_id, ScenarioRunStartSolution)

Start a new scenariorun for a Solution Run Template

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **ScenarioRunStartSolution** | [**ScenarioRunStartSolution**](../Models/ScenarioRunStartSolution.md)| the Solution Run Template information to start |

### Return type

[**ScenarioRun**](../Models/ScenarioRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

