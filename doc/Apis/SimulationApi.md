# SimulationApi

All URIs are relative to *https://api.azure.cosmo-platform.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**deleteSimulation**](SimulationApi.md#deleteSimulation) | **DELETE** /organizations/{organization_id}/simulations/{simulation_id} | Delete a simulation
[**findSimulationById**](SimulationApi.md#findSimulationById) | **GET** /organizations/{organization_id}/simulations/{simulation_id} | Get the details of a simulation
[**getScenarioSimulation**](SimulationApi.md#getScenarioSimulation) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/simulations/{simulation_id} | get the Simulation for the Scenario
[**getScenarioSimulationLogs**](SimulationApi.md#getScenarioSimulationLogs) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/simulations/{simulation_id}/logs | get the logs for the Simulation
[**getScenarioSimulations**](SimulationApi.md#getScenarioSimulations) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/simulations | get the list of Simulations for the Scenario
[**getWorkspaceSimulations**](SimulationApi.md#getWorkspaceSimulations) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/simulations | get the list of Simulations for the Workspace
[**runScenario**](SimulationApi.md#runScenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/run | run a Simulation for the Scenario
[**searchSimulationLogs**](SimulationApi.md#searchSimulationLogs) | **POST** /organizations/{organization_id}/simulations/{simulation_id}/logs/search | Search the logs of a simulation
[**searchSimulations**](SimulationApi.md#searchSimulations) | **POST** /organizations/{organization_id}/simulations/search | Search Simulations
[**startSimulationContainers**](SimulationApi.md#startSimulationContainers) | **POST** /organizations/{organization_id}/simulations/startcontainers | Start a new simulation with raw containers definition
[**startSimulationScenario**](SimulationApi.md#startSimulationScenario) | **POST** /organizations/{organization_id}/simulations/start | Start a new simulation for a Scenario
[**startSimulationSimulator**](SimulationApi.md#startSimulationSimulator) | **POST** /organizations/{organization_id}/simulations/startsimulator | Start a new simulation for a Simulator Analysis


<a name="deleteSimulation"></a>
# **deleteSimulation**
> Simulation deleteSimulation(organization\_id, simulation\_id)

Delete a simulation

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **simulation\_id** | **String**| the Simulation identifier | [default to null]

### Return type

[**Simulation**](../Models/Simulation.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findSimulationById"></a>
# **findSimulationById**
> Simulation findSimulationById(organization\_id, simulation\_id)

Get the details of a simulation

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **simulation\_id** | **String**| the Simulation identifier | [default to null]

### Return type

[**Simulation**](../Models/Simulation.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioSimulation"></a>
# **getScenarioSimulation**
> Simulation getScenarioSimulation(organization\_id, workspace\_id, scenario\_id, simulation\_id)

get the Simulation for the Scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **simulation\_id** | **String**| the Simulation identifier | [default to null]

### Return type

[**Simulation**](../Models/Simulation.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioSimulationLogs"></a>
# **getScenarioSimulationLogs**
> SimulationLogs getScenarioSimulationLogs(organization\_id, workspace\_id, scenario\_id, simulation\_id)

get the logs for the Simulation

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **simulation\_id** | **String**| the Simulation identifier | [default to null]

### Return type

[**SimulationLogs**](../Models/SimulationLogs.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioSimulations"></a>
# **getScenarioSimulations**
> List getScenarioSimulations(organization\_id, workspace\_id, scenario\_id)

get the list of Simulations for the Scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]

### Return type

[**List**](../Models/SimulationBase.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getWorkspaceSimulations"></a>
# **getWorkspaceSimulations**
> List getWorkspaceSimulations(organization\_id, workspace\_id)

get the list of Simulations for the Workspace

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]

### Return type

[**List**](../Models/SimulationBase.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="runScenario"></a>
# **runScenario**
> SimulationBase runScenario(organization\_id, workspace\_id, scenario\_id)

run a Simulation for the Scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]

### Return type

[**SimulationBase**](../Models/SimulationBase.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="searchSimulationLogs"></a>
# **searchSimulationLogs**
> SimulationLogs searchSimulationLogs(organization\_id, simulation\_id, SimulationLogsOptions)

Search the logs of a simulation

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **simulation\_id** | **String**| the Simulation identifier | [default to null]
 **SimulationLogsOptions** | [**SimulationLogsOptions**](../Models/SimulationLogsOptions.md)| the options to search logs |

### Return type

[**SimulationLogs**](../Models/SimulationLogs.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="searchSimulations"></a>
# **searchSimulations**
> List searchSimulations(organization\_id, SimulationSearch)

Search Simulations

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **SimulationSearch** | [**SimulationSearch**](../Models/SimulationSearch.md)| the Simulation search parameters |

### Return type

[**List**](../Models/SimulationBase.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="startSimulationContainers"></a>
# **startSimulationContainers**
> Simulation startSimulationContainers(organization\_id, SimulationStartContainers)

Start a new simulation with raw containers definition

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **SimulationStartContainers** | [**SimulationStartContainers**](../Models/SimulationStartContainers.md)| the raw containers definition |

### Return type

[**Simulation**](../Models/Simulation.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="startSimulationScenario"></a>
# **startSimulationScenario**
> Simulation startSimulationScenario(organization\_id, SimulationStartScenario)

Start a new simulation for a Scenario

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **SimulationStartScenario** | [**SimulationStartScenario**](../Models/SimulationStartScenario.md)| the Scenario information to start |

### Return type

[**Simulation**](../Models/Simulation.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="startSimulationSimulator"></a>
# **startSimulationSimulator**
> Simulation startSimulationSimulator(organization\_id, SimulationStartSimulator)

Start a new simulation for a Simulator Analysis

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **SimulationStartSimulator** | [**SimulationStartSimulator**](../Models/SimulationStartSimulator.md)| the Simulator Analysis information to start |

### Return type

[**Simulation**](../Models/Simulation.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

