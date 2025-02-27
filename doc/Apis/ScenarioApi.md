# ScenarioApi

All URIs are relative to *https://dev.api.cosmotech.com*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addOrReplaceScenarioParameterValues**](ScenarioApi.md#addOrReplaceScenarioParameterValues) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/parameterValues | Add (or replace) Parameter Values for the Scenario specified |
| [**addScenarioAccessControl**](ScenarioApi.md#addScenarioAccessControl) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access | Add a control access to the Scenario |
| [**compareScenarios**](ScenarioApi.md#compareScenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/compare/{compared_scenario_id} | Compare the Scenario with another one and returns the difference for parameters values |
| [**createScenario**](ScenarioApi.md#createScenario) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Create a new Scenario |
| [**deleteAllScenarios**](ScenarioApi.md#deleteAllScenarios) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | Delete all Scenarios of the Workspace |
| [**deleteScenario**](ScenarioApi.md#deleteScenario) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Delete a scenario |
| [**downloadScenarioData**](ScenarioApi.md#downloadScenarioData) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/downloads | Download Scenario data |
| [**findAllScenarios**](ScenarioApi.md#findAllScenarios) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios | List all Scenarios |
| [**findAllScenariosByValidationStatus**](ScenarioApi.md#findAllScenariosByValidationStatus) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/{validationStatus} | List all Scenarios by validation status |
| [**findScenarioById**](ScenarioApi.md#findScenarioById) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Get the details of an scenario |
| [**getScenarioAccessControl**](ScenarioApi.md#getScenarioAccessControl) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access/{identity_id} | Get a control access for the Scenario |
| [**getScenarioDataDownloadJobInfo**](ScenarioApi.md#getScenarioDataDownloadJobInfo) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/downloads/{download_id} | Get Scenario data download URL |
| [**getScenarioPermissions**](ScenarioApi.md#getScenarioPermissions) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/permissions/{role} | Get the Scenario permission by given role |
| [**getScenarioSecurity**](ScenarioApi.md#getScenarioSecurity) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security | Get the Scenario security information |
| [**getScenarioSecurityUsers**](ScenarioApi.md#getScenarioSecurityUsers) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/users | Get the Scenario security users list |
| [**getScenarioValidationStatusById**](ScenarioApi.md#getScenarioValidationStatusById) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/ValidationStatus | Get the validation status of an scenario |
| [**getScenariosTree**](ScenarioApi.md#getScenariosTree) | **GET** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/tree | Get the Scenarios Tree |
| [**removeAllScenarioParameterValues**](ScenarioApi.md#removeAllScenarioParameterValues) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/parameterValues | Remove all Parameter Values from the Scenario specified |
| [**removeScenarioAccessControl**](ScenarioApi.md#removeScenarioAccessControl) | **DELETE** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access/{identity_id} | Remove the specified access from the given Organization Scenario |
| [**setScenarioDefaultSecurity**](ScenarioApi.md#setScenarioDefaultSecurity) | **POST** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/default | Set the Scenario default security |
| [**updateScenario**](ScenarioApi.md#updateScenario) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id} | Update a scenario |
| [**updateScenarioAccessControl**](ScenarioApi.md#updateScenarioAccessControl) | **PATCH** /organizations/{organization_id}/workspaces/{workspace_id}/scenarios/{scenario_id}/security/access/{identity_id} | Update the specified access to User for a Scenario |


<a name="addOrReplaceScenarioParameterValues"></a>
# **addOrReplaceScenarioParameterValues**
> List addOrReplaceScenarioParameterValues(organization\_id, workspace\_id, scenario\_id, ScenarioRunTemplateParameterValue)

Add (or replace) Parameter Values for the Scenario specified

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **ScenarioRunTemplateParameterValue** | [**List**](../Models/ScenarioRunTemplateParameterValue.md)| the Parameter Value to add. Any Parameter Value with the same ID is overwritten | |

### Return type

[**List**](../Models/ScenarioRunTemplateParameterValue.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="addScenarioAccessControl"></a>
# **addScenarioAccessControl**
> ScenarioAccessControl addScenarioAccessControl(organization\_id, workspace\_id, scenario\_id, ScenarioAccessControl)

Add a control access to the Scenario

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **ScenarioAccessControl** | [**ScenarioAccessControl**](../Models/ScenarioAccessControl.md)| the new Scenario security access to add. | |

### Return type

[**ScenarioAccessControl**](../Models/ScenarioAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="compareScenarios"></a>
# **compareScenarios**
> ScenarioComparisonResult compareScenarios(organization\_id, workspace\_id, scenario\_id, compared\_scenario\_id)

Compare the Scenario with another one and returns the difference for parameters values

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **compared\_scenario\_id** | **String**| the Scenario identifier to compare to | [default to null] |

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

Create a new Scenario

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **Scenario** | [**Scenario**](../Models/Scenario.md)| the Scenario to create | |

### Return type

[**Scenario**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="deleteAllScenarios"></a>
# **deleteAllScenarios**
> deleteAllScenarios(organization\_id, workspace\_id)

Delete all Scenarios of the Workspace

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteScenario"></a>
# **deleteScenario**
> deleteScenario(organization\_id, workspace\_id, scenario\_id)

Delete a scenario

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="downloadScenarioData"></a>
# **downloadScenarioData**
> ScenarioDataDownloadJob downloadScenarioData(organization\_id, workspace\_id, scenario\_id)

Download Scenario data

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |

### Return type

[**ScenarioDataDownloadJob**](../Models/ScenarioDataDownloadJob.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findAllScenarios"></a>
# **findAllScenarios**
> List findAllScenarios(organization\_id, workspace\_id, page, size)

List all Scenarios

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findAllScenariosByValidationStatus"></a>
# **findAllScenariosByValidationStatus**
> List findAllScenariosByValidationStatus(organization\_id, workspace\_id, validationStatus, page, size)

List all Scenarios by validation status

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **validationStatus** | [**ScenarioValidationStatus**](../Models/.md)| the Scenario Validation Status | [default to null] [enum: Draft, Rejected, Unknown, Validated] |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

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

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |

### Return type

[**Scenario**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioAccessControl"></a>
# **getScenarioAccessControl**
> ScenarioAccessControl getScenarioAccessControl(organization\_id, workspace\_id, scenario\_id, identity\_id)

Get a control access for the Scenario

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

[**ScenarioAccessControl**](../Models/ScenarioAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioDataDownloadJobInfo"></a>
# **getScenarioDataDownloadJobInfo**
> ScenarioDataDownloadInfo getScenarioDataDownloadJobInfo(organization\_id, workspace\_id, scenario\_id, download\_id)

Get Scenario data download URL

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **download\_id** | **String**| the Scenario Download identifier | [default to null] |

### Return type

[**ScenarioDataDownloadInfo**](../Models/ScenarioDataDownloadInfo.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioPermissions"></a>
# **getScenarioPermissions**
> List getScenarioPermissions(organization\_id, workspace\_id, scenario\_id, role)

Get the Scenario permission by given role

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **role** | **String**| the Role | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioSecurity"></a>
# **getScenarioSecurity**
> ScenarioSecurity getScenarioSecurity(organization\_id, workspace\_id, scenario\_id)

Get the Scenario security information

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |

### Return type

[**ScenarioSecurity**](../Models/ScenarioSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioSecurityUsers"></a>
# **getScenarioSecurityUsers**
> List getScenarioSecurityUsers(organization\_id, workspace\_id, scenario\_id)

Get the Scenario security users list

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getScenarioValidationStatusById"></a>
# **getScenarioValidationStatusById**
> ScenarioValidationStatus getScenarioValidationStatusById(organization\_id, workspace\_id, scenario\_id)

Get the validation status of an scenario

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |

### Return type

[**ScenarioValidationStatus**](../Models/ScenarioValidationStatus.md)

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

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |

### Return type

[**List**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="removeAllScenarioParameterValues"></a>
# **removeAllScenarioParameterValues**
> removeAllScenarioParameterValues(organization\_id, workspace\_id, scenario\_id)

Remove all Parameter Values from the Scenario specified

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="removeScenarioAccessControl"></a>
# **removeScenarioAccessControl**
> removeScenarioAccessControl(organization\_id, workspace\_id, scenario\_id, identity\_id)

Remove the specified access from the given Organization Scenario

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="setScenarioDefaultSecurity"></a>
# **setScenarioDefaultSecurity**
> ScenarioSecurity setScenarioDefaultSecurity(organization\_id, workspace\_id, scenario\_id, ScenarioRole)

Set the Scenario default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **ScenarioRole** | [**ScenarioRole**](../Models/ScenarioRole.md)| This change the scenario default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the scenario. | |

### Return type

[**ScenarioSecurity**](../Models/ScenarioSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateScenario"></a>
# **updateScenario**
> Scenario updateScenario(organization\_id, workspace\_id, scenario\_id, Scenario)

Update a scenario

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **Scenario** | [**Scenario**](../Models/Scenario.md)| The new Scenario details. This endpoint can&#39;t be used to update :   - id   - ownerId   - datasetList   - solutionId   - runTemplateId   - parametersValues   - security  | |

### Return type

[**Scenario**](../Models/Scenario.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateScenarioAccessControl"></a>
# **updateScenarioAccessControl**
> ScenarioAccessControl updateScenarioAccessControl(organization\_id, workspace\_id, scenario\_id, identity\_id, ScenarioRole)

Update the specified access to User for a Scenario

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **workspace\_id** | **String**| the Workspace identifier | [default to null] |
| **scenario\_id** | **String**| the Scenario identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |
| **ScenarioRole** | [**ScenarioRole**](../Models/ScenarioRole.md)| The new Scenario Access Control | |

### Return type

[**ScenarioAccessControl**](../Models/ScenarioAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

