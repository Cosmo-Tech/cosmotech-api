# ScenariorunresultApi

All URIs are relative to *https://dev.api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
<<<<<<< HEAD
[**getScenarioRunResult**](ScenariorunresultApi.md#getScenarioRunResult) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns/{scenariorun_id}/probes/{probe_id} | Get a ScenarioRunResult in the Organization
[**sendScenarioRunResult**](ScenariorunresultApi.md#sendScenarioRunResult) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/scenarioruns/{scenariorun_id}/probes/{probe_id} | Create a new ScenarioRunResult in the Organization


<a name="getScenarioRunResult"></a>
# **getScenarioRunResult**
> ScenarioRunResult getScenarioRunResult(organization\_id, workspace\_id, scenario\_id, scenariorun\_id, probe\_id)

Get a ScenarioRunResult in the Organization
=======
[**createScenarioRunResult**](ScenariorunresultApi.md#createScenarioRunResult) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarioruns/{scenariorun_id}/probes/{probe_id} | Create a new ScenarioRunResult in the Organization
[**getScenarioRunResult**](ScenariorunresultApi.md#getScenarioRunResult) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarioruns/{scenariorun_id}/probes/{probe_id} | Get a ScenarioRunResult in the Organization


<a name="createScenarioRunResult"></a>
# **createScenarioRunResult**
> createScenarioRunResult(organization\_id, workspace\_id, scenariorun\_id, probe\_id, ScenarioRunResult)

Create a new ScenarioRunResult in the Organization
>>>>>>> 3c68d2d6 (add basic functionnalities for get and post endpoints)

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
<<<<<<< HEAD
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]
 **probe\_id** | **String**| the Probe identifier | [default to null]

### Return type

[**ScenarioRunResult**](../Models/ScenarioRunResult.md)
=======
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]
 **probe\_id** | **String**| the Probe identifier | [default to null]
 **ScenarioRunResult** | [**ScenarioRunResult**](../Models/ScenarioRunResult.md)| the ScenarioRunResult to register |

### Return type

null (empty response body)
>>>>>>> 3c68d2d6 (add basic functionnalities for get and post endpoints)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

<<<<<<< HEAD
- **Content-Type**: Not defined
- **Accept**: application/json

<a name="sendScenarioRunResult"></a>
# **sendScenarioRunResult**
> ScenarioRunResult sendScenarioRunResult(organization\_id, workspace\_id, scenario\_id, scenariorun\_id, probe\_id, request\_body)

Create a new ScenarioRunResult in the Organization
=======
- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getScenarioRunResult"></a>
# **getScenarioRunResult**
> ScenarioRunResult getScenarioRunResult(organization\_id, workspace\_id, scenariorun\_id, probe\_id)

Get a ScenarioRunResult in the Organization
>>>>>>> 3c68d2d6 (add basic functionnalities for get and post endpoints)

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
<<<<<<< HEAD
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]
 **probe\_id** | **String**| the Probe identifier | [default to null]
 **request\_body** | [**Map**](../Models/string.md)| the ScenarioRunResult to register |
=======
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]
 **probe\_id** | **String**| the Probe identifier | [default to null]
>>>>>>> 3c68d2d6 (add basic functionnalities for get and post endpoints)

### Return type

[**ScenarioRunResult**](../Models/ScenarioRunResult.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

<<<<<<< HEAD
- **Content-Type**: application/json
=======
- **Content-Type**: Not defined
>>>>>>> 3c68d2d6 (add basic functionnalities for get and post endpoints)
- **Accept**: application/json

