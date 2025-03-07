# SolutionApi

All URIs are relative to *https://dev.api.cosmotech.com*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addOrReplaceParameterGroups**](SolutionApi.md#addOrReplaceParameterGroups) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Add Parameter Groups. Any item with the same ID will be overwritten |
| [**addOrReplaceParameters**](SolutionApi.md#addOrReplaceParameters) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameters | Add Parameters. Any item with the same ID will be overwritten |
| [**addOrReplaceRunTemplates**](SolutionApi.md#addOrReplaceRunTemplates) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Add Run Templates. Any item with the same ID will be overwritten |
| [**addSolutionAccessControl**](SolutionApi.md#addSolutionAccessControl) | **POST** /organizations/{organization_id}/solutions/{solution_id}/security/access | Add a control access to the Solution |
| [**createSolution**](SolutionApi.md#createSolution) | **POST** /organizations/{organization_id}/solutions | Register a new solution |
| [**deleteSolution**](SolutionApi.md#deleteSolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution |
| [**deleteSolutionRunTemplate**](SolutionApi.md#deleteSolutionRunTemplate) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Remove the specified Solution Run Template |
| [**downloadRunTemplateHandler**](SolutionApi.md#downloadRunTemplateHandler) | **GET** /organizations/{organization_id}/solutions/{solution_id}/runtemplates/{run_template_id}/handlers/{handler_id}/download | Download a Run Template step handler zip file |
| [**findAllSolutions**](SolutionApi.md#findAllSolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions |
| [**findSolutionById**](SolutionApi.md#findSolutionById) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution |
| [**getSolutionAccessControl**](SolutionApi.md#getSolutionAccessControl) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Get a control access for the Solution |
| [**getSolutionSecurity**](SolutionApi.md#getSolutionSecurity) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security | Get the Solution security information |
| [**getSolutionSecurityUsers**](SolutionApi.md#getSolutionSecurityUsers) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/users | Get the Solution security users list |
| [**removeAllRunTemplates**](SolutionApi.md#removeAllRunTemplates) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Remove all Run Templates from the Solution specified |
| [**removeAllSolutionParameterGroups**](SolutionApi.md#removeAllSolutionParameterGroups) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Remove all Parameter Groups from the Solution specified |
| [**removeAllSolutionParameters**](SolutionApi.md#removeAllSolutionParameters) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameters | Remove all Parameters from the Solution specified |
| [**removeSolutionAccessControl**](SolutionApi.md#removeSolutionAccessControl) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Remove the specified access from the given Organization Solution |
| [**setSolutionDefaultSecurity**](SolutionApi.md#setSolutionDefaultSecurity) | **POST** /organizations/{organization_id}/solutions/{solution_id}/security/default | Set the Solution default security |
| [**updateSolution**](SolutionApi.md#updateSolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution |
| [**updateSolutionAccessControl**](SolutionApi.md#updateSolutionAccessControl) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Update the specified access to User for a Solution |
| [**updateSolutionRunTemplate**](SolutionApi.md#updateSolutionRunTemplate) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Update the specified Solution Run Template |
| [**uploadRunTemplateHandler**](SolutionApi.md#uploadRunTemplateHandler) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runtemplates/{run_template_id}/handlers/{handler_id}/upload | Upload a Run Template step handler zip file |


<a name="addOrReplaceParameterGroups"></a>
# **addOrReplaceParameterGroups**
> List addOrReplaceParameterGroups(organization\_id, solution\_id, RunTemplateParameterGroup)

Add Parameter Groups. Any item with the same ID will be overwritten

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **RunTemplateParameterGroup** | [**List**](../Models/RunTemplateParameterGroup.md)| the Parameter Groups | |

### Return type

[**List**](../Models/RunTemplateParameterGroup.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="addOrReplaceParameters"></a>
# **addOrReplaceParameters**
> List addOrReplaceParameters(organization\_id, solution\_id, RunTemplateParameter)

Add Parameters. Any item with the same ID will be overwritten

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **RunTemplateParameter** | [**List**](../Models/RunTemplateParameter.md)| the Parameters | |

### Return type

[**List**](../Models/RunTemplateParameter.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="addOrReplaceRunTemplates"></a>
# **addOrReplaceRunTemplates**
> List addOrReplaceRunTemplates(organization\_id, solution\_id, RunTemplate)

Add Run Templates. Any item with the same ID will be overwritten

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **RunTemplate** | [**List**](../Models/RunTemplate.md)| the Run Templates | |

### Return type

[**List**](../Models/RunTemplate.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="addSolutionAccessControl"></a>
# **addSolutionAccessControl**
> SolutionAccessControl addSolutionAccessControl(organization\_id, solution\_id, SolutionAccessControl)

Add a control access to the Solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **SolutionAccessControl** | [**SolutionAccessControl**](../Models/SolutionAccessControl.md)| the new Solution security access to add. | |

### Return type

[**SolutionAccessControl**](../Models/SolutionAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="createSolution"></a>
# **createSolution**
> Solution createSolution(organization\_id, Solution)

Register a new solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **Solution** | [**Solution**](../Models/Solution.md)| the Solution to create | |

### Return type

[**Solution**](../Models/Solution.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="deleteSolution"></a>
# **deleteSolution**
> deleteSolution(organization\_id, solution\_id)

Delete a solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteSolutionRunTemplate"></a>
# **deleteSolutionRunTemplate**
> deleteSolutionRunTemplate(organization\_id, solution\_id, run\_template\_id)

Remove the specified Solution Run Template

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **run\_template\_id** | **String**| the Run Template identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="downloadRunTemplateHandler"></a>
# **downloadRunTemplateHandler**
> byte[] downloadRunTemplateHandler(organization\_id, solution\_id, run\_template\_id, handler\_id)

Download a Run Template step handler zip file

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **run\_template\_id** | **String**| the Run Template identifier | [default to null] |
| **handler\_id** | [**RunTemplateHandlerId**](../Models/.md)| the Handler identifier | [default to null] [enum: parameters_handler, validator, prerun, engine, postrun, scenariodata_transform] |

### Return type

**byte[]**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/octet-stream

<a name="findAllSolutions"></a>
# **findAllSolutions**
> List findAllSolutions(organization\_id, page, size)

List all Solutions

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **page** | **Integer**| page number to query | [optional] [default to null] |
| **size** | **Integer**| amount of result by page | [optional] [default to null] |

### Return type

[**List**](../Models/Solution.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findSolutionById"></a>
# **findSolutionById**
> Solution findSolutionById(organization\_id, solution\_id)

Get the details of a solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |

### Return type

[**Solution**](../Models/Solution.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getSolutionAccessControl"></a>
# **getSolutionAccessControl**
> SolutionAccessControl getSolutionAccessControl(organization\_id, solution\_id, identity\_id)

Get a control access for the Solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

[**SolutionAccessControl**](../Models/SolutionAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getSolutionSecurity"></a>
# **getSolutionSecurity**
> SolutionSecurity getSolutionSecurity(organization\_id, solution\_id)

Get the Solution security information

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |

### Return type

[**SolutionSecurity**](../Models/SolutionSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getSolutionSecurityUsers"></a>
# **getSolutionSecurityUsers**
> List getSolutionSecurityUsers(organization\_id, solution\_id)

Get the Solution security users list

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="removeAllRunTemplates"></a>
# **removeAllRunTemplates**
> removeAllRunTemplates(organization\_id, solution\_id)

Remove all Run Templates from the Solution specified

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="removeAllSolutionParameterGroups"></a>
# **removeAllSolutionParameterGroups**
> removeAllSolutionParameterGroups(organization\_id, solution\_id)

Remove all Parameter Groups from the Solution specified

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="removeAllSolutionParameters"></a>
# **removeAllSolutionParameters**
> removeAllSolutionParameters(organization\_id, solution\_id)

Remove all Parameters from the Solution specified

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="removeSolutionAccessControl"></a>
# **removeSolutionAccessControl**
> removeSolutionAccessControl(organization\_id, solution\_id, identity\_id)

Remove the specified access from the given Organization Solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="setSolutionDefaultSecurity"></a>
# **setSolutionDefaultSecurity**
> SolutionSecurity setSolutionDefaultSecurity(organization\_id, solution\_id, SolutionRole)

Set the Solution default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **SolutionRole** | [**SolutionRole**](../Models/SolutionRole.md)| This change the solution default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the solution. | |

### Return type

[**SolutionSecurity**](../Models/SolutionSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateSolution"></a>
# **updateSolution**
> Solution updateSolution(organization\_id, solution\_id, Solution)

Update a solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **Solution** | [**Solution**](../Models/Solution.md)| the new Solution details. This endpoint can&#39;t be used to update :   - id   - runTemplates   - security  | |

### Return type

[**Solution**](../Models/Solution.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateSolutionAccessControl"></a>
# **updateSolutionAccessControl**
> SolutionAccessControl updateSolutionAccessControl(organization\_id, solution\_id, identity\_id, SolutionRole)

Update the specified access to User for a Solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |
| **SolutionRole** | [**SolutionRole**](../Models/SolutionRole.md)| The new Solution Access Control | |

### Return type

[**SolutionAccessControl**](../Models/SolutionAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateSolutionRunTemplate"></a>
# **updateSolutionRunTemplate**
> List updateSolutionRunTemplate(organization\_id, solution\_id, run\_template\_id, RunTemplate)

Update the specified Solution Run Template

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **run\_template\_id** | **String**| the Run Template identifier | [default to null] |
| **RunTemplate** | [**RunTemplate**](../Models/RunTemplate.md)| the Run Templates | |

### Return type

[**List**](../Models/RunTemplate.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="uploadRunTemplateHandler"></a>
# **uploadRunTemplateHandler**
> uploadRunTemplateHandler(organization\_id, solution\_id, run\_template\_id, handler\_id, body, overwrite)

Upload a Run Template step handler zip file

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **run\_template\_id** | **String**| the Run Template identifier | [default to null] |
| **handler\_id** | [**RunTemplateHandlerId**](../Models/.md)| the Handler identifier | [default to null] [enum: parameters_handler, validator, prerun, engine, postrun, scenariodata_transform] |
| **body** | **File**|  | |
| **overwrite** | **Boolean**| whether to overwrite any existing handler resource | [optional] [default to false] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/octet-stream
- **Accept**: Not defined

