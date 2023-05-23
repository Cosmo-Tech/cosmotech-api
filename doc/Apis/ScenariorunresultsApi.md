# ScenariorunresultsApi

All URIs are relative to *https://dev.api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createScenarioRunResults**](ScenariorunresultsApi.md#createScenarioRunResults) | **POST** /organizations/{organization_id}/workspace/{workspace_id}/scenario/{scenario_id}/scenariorun/{scenariorun_id}/scenariorunresults/{scenariorunresults_id} | Create a new ScenarioRunResult in the Organization
[**getScenarioRunResults**](ScenariorunresultsApi.md#getScenarioRunResults) | **GET** /organizations/{organization_id}/workspace/{workspace_id}/scenario/{scenario_id}/scenariorun/{scenariorun_id}/scenariorunresults/{scenariorunresults_id} | Get a ScenarioRunResult in the Organization


<a name="createScenarioRunResults"></a>
# **createScenarioRunResults**
> createScenarioRunResults(organization\_id, workspace\_id, scenario\_id, scenariorun\_id, scenariorunresults\_id)

Create a new ScenarioRunResult in the Organization

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]
 **scenariorunresults\_id** | **String**| the ScenarioRunResults identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="getScenarioRunResults"></a>
# **getScenarioRunResults**
> getScenarioRunResults(organization\_id, workspace\_id, scenario\_id, scenariorun\_id, scenariorunresults\_id)

Get a ScenarioRunResult in the Organization

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **workspace\_id** | **String**| the Workspace identifier | [default to null]
 **scenario\_id** | **String**| the Scenario identifier | [default to null]
 **scenariorun\_id** | **String**| the ScenarioRun identifier | [default to null]
 **scenariorunresults\_id** | **String**| the ScenarioRunResults identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

