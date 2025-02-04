# SolutionApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createSolution**](SolutionApi.md#createSolution) | **POST** /organizations/{organization_id}/solutions | Create a new solution |
| [**createSolutionAccessControl**](SolutionApi.md#createSolutionAccessControl) | **POST** /organizations/{organization_id}/solutions/{solution_id}/security/access | Create solution access control |
| [**deleteSolution**](SolutionApi.md#deleteSolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution |
| [**deleteSolutionAccessControl**](SolutionApi.md#deleteSolutionAccessControl) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Delete solution access control |
| [**deleteSolutionParameterGroups**](SolutionApi.md#deleteSolutionParameterGroups) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Delete all parameter groups from the solution |
| [**deleteSolutionParameters**](SolutionApi.md#deleteSolutionParameters) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameters | Delete all parameters from the solution |
| [**deleteSolutionRunTemplate**](SolutionApi.md#deleteSolutionRunTemplate) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Delete a specific run template |
| [**deleteSolutionRunTemplates**](SolutionApi.md#deleteSolutionRunTemplates) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Delete all run templates from the solution |
| [**getSolution**](SolutionApi.md#getSolution) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution |
| [**getSolutionAccessControl**](SolutionApi.md#getSolutionAccessControl) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Get solution access control |
| [**getSolutionSecurity**](SolutionApi.md#getSolutionSecurity) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security | Get solution security information |
| [**listSolutionSecurityUsers**](SolutionApi.md#listSolutionSecurityUsers) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/users | List solution security users |
| [**listSolutions**](SolutionApi.md#listSolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions |
| [**updateSolution**](SolutionApi.md#updateSolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution |
| [**updateSolutionAccessControl**](SolutionApi.md#updateSolutionAccessControl) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Update solution access control |
| [**updateSolutionDefaultSecurity**](SolutionApi.md#updateSolutionDefaultSecurity) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/default | Update solution default security |
| [**updateSolutionParameterGroups**](SolutionApi.md#updateSolutionParameterGroups) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Update solution parameter groups |
| [**updateSolutionParameters**](SolutionApi.md#updateSolutionParameters) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/parameters | Update solution parameters |
| [**updateSolutionRunTemplate**](SolutionApi.md#updateSolutionRunTemplate) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Update a specific run template |
| [**updateSolutionRunTemplates**](SolutionApi.md#updateSolutionRunTemplates) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Update solution run templates |


<a name="createSolution"></a>
# **createSolution**
> Solution createSolution(organization\_id, SolutionCreateRequest)

Create a new solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **SolutionCreateRequest** | [**SolutionCreateRequest**](../Models/SolutionCreateRequest.md)| the Solution to create | |

### Return type

[**Solution**](../Models/Solution.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="createSolutionAccessControl"></a>
# **createSolutionAccessControl**
> SolutionAccessControl createSolutionAccessControl(organization\_id, solution\_id, SolutionAccessControl)

Create solution access control

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **SolutionAccessControl** | [**SolutionAccessControl**](../Models/SolutionAccessControl.md)| Access control to create | |

### Return type

[**SolutionAccessControl**](../Models/SolutionAccessControl.md)

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

<a name="deleteSolutionAccessControl"></a>
# **deleteSolutionAccessControl**
> deleteSolutionAccessControl(organization\_id, solution\_id, identity\_id)

Delete solution access control

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

<a name="deleteSolutionParameterGroups"></a>
# **deleteSolutionParameterGroups**
> deleteSolutionParameterGroups(organization\_id, solution\_id)

Delete all parameter groups from the solution

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

<a name="deleteSolutionParameters"></a>
# **deleteSolutionParameters**
> deleteSolutionParameters(organization\_id, solution\_id)

Delete all parameters from the solution

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

Delete a specific run template

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

<a name="deleteSolutionRunTemplates"></a>
# **deleteSolutionRunTemplates**
> deleteSolutionRunTemplates(organization\_id, solution\_id)

Delete all run templates from the solution

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

<a name="getSolution"></a>
# **getSolution**
> Solution getSolution(organization\_id, solution\_id)

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

Get solution access control

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

Get solution security information

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

<a name="listSolutionSecurityUsers"></a>
# **listSolutionSecurityUsers**
> List listSolutionSecurityUsers(organization\_id, solution\_id)

List solution security users

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

<a name="listSolutions"></a>
# **listSolutions**
> List listSolutions(organization\_id, page, size)

List all Solutions

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **page** | **Integer**| Page number to query (zero-based indexing) | [optional] [default to null] |
| **size** | **Integer**| Number of records per page | [optional] [default to null] |

### Return type

[**List**](../Models/Solution.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="updateSolution"></a>
# **updateSolution**
> Solution updateSolution(organization\_id, solution\_id, SolutionUpdateRequest)

Update a solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **SolutionUpdateRequest** | [**SolutionUpdateRequest**](../Models/SolutionUpdateRequest.md)| the new Solution details. This endpoint can&#39;t be used to update security | |

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

Update solution access control

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **identity\_id** | **String**| the User identifier | [default to null] |
| **SolutionRole** | [**SolutionRole**](../Models/SolutionRole.md)| Access control updates | |

### Return type

[**SolutionAccessControl**](../Models/SolutionAccessControl.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="updateSolutionDefaultSecurity"></a>
# **updateSolutionDefaultSecurity**
> SolutionSecurity updateSolutionDefaultSecurity(organization\_id, solution\_id, SolutionRole)

Update solution default security

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **SolutionRole** | [**SolutionRole**](../Models/SolutionRole.md)| This changes the solution default security. The default security is the role assigned to any person not on the Access Control List. If the default security is None, then nobody outside of the ACL can access the solution. | |

### Return type

[**SolutionSecurity**](../Models/SolutionSecurity.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateSolutionParameterGroups"></a>
# **updateSolutionParameterGroups**
> List updateSolutionParameterGroups(organization\_id, solution\_id, RunTemplateParameterGroup)

Update solution parameter groups

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **RunTemplateParameterGroup** | [**List**](../Models/RunTemplateParameterGroup.md)| Parameter groups to update | |

### Return type

[**List**](../Models/RunTemplateParameterGroup.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="updateSolutionParameters"></a>
# **updateSolutionParameters**
> List updateSolutionParameters(organization\_id, solution\_id, RunTemplateParameter)

Update solution parameters

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **RunTemplateParameter** | [**List**](../Models/RunTemplateParameter.md)| Parameters to update | |

### Return type

[**List**](../Models/RunTemplateParameter.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="updateSolutionRunTemplate"></a>
# **updateSolutionRunTemplate**
> List updateSolutionRunTemplate(organization\_id, solution\_id, run\_template\_id, RunTemplate)

Update a specific run template

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **run\_template\_id** | **String**| the Run Template identifier | [default to null] |
| **RunTemplate** | [**RunTemplate**](../Models/RunTemplate.md)| Run template updates | |

### Return type

[**List**](../Models/RunTemplate.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="updateSolutionRunTemplates"></a>
# **updateSolutionRunTemplates**
> List updateSolutionRunTemplates(organization\_id, solution\_id, RunTemplate)

Update solution run templates

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **RunTemplate** | [**List**](../Models/RunTemplate.md)| Run templates to update | |

### Return type

[**List**](../Models/RunTemplate.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

