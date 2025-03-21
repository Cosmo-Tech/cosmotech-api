# SolutionApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createSolution**](SolutionApi.md#createSolution) | **POST** /organizations/{organization_id}/solutions | Create a new solution |
| [**createSolutionAccessControl**](SolutionApi.md#createSolutionAccessControl) | **POST** /organizations/{organization_id}/solutions/{solution_id}/security/access | Create solution access control |
| [**createSolutionParameter**](SolutionApi.md#createSolutionParameter) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameters | Create solution parameter for a solution |
| [**createSolutionParameterGroup**](SolutionApi.md#createSolutionParameterGroup) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Create a solution parameter group |
| [**deleteSolution**](SolutionApi.md#deleteSolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution |
| [**deleteSolutionAccessControl**](SolutionApi.md#deleteSolutionAccessControl) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Delete solution access control |
| [**deleteSolutionParameter**](SolutionApi.md#deleteSolutionParameter) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameters/{parameter_id} | Delete specific parameter from the solution |
| [**deleteSolutionParameterGroup**](SolutionApi.md#deleteSolutionParameterGroup) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups/{parameter_group_id} | Delete a parameter group from the solution |
| [**deleteSolutionRunTemplate**](SolutionApi.md#deleteSolutionRunTemplate) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id} | Delete a specific run template |
| [**deleteSolutionRunTemplates**](SolutionApi.md#deleteSolutionRunTemplates) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Delete all run templates from the solution |
| [**getSolution**](SolutionApi.md#getSolution) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution |
| [**getSolutionAccessControl**](SolutionApi.md#getSolutionAccessControl) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Get solution access control |
| [**getSolutionParameter**](SolutionApi.md#getSolutionParameter) | **GET** /organizations/{organization_id}/solutions/{solution_id}/parameters/{parameter_id} | Get the details of a solution parameter |
| [**getSolutionParameterGroup**](SolutionApi.md#getSolutionParameterGroup) | **GET** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups/{parameter_group_id} | Get details of a solution parameter group |
| [**getSolutionSecurity**](SolutionApi.md#getSolutionSecurity) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security | Get solution security information |
| [**listSolutionParameterGroups**](SolutionApi.md#listSolutionParameterGroups) | **GET** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | List all solution parameter groups |
| [**listSolutionParameters**](SolutionApi.md#listSolutionParameters) | **GET** /organizations/{organization_id}/solutions/{solution_id}/parameters | List all solution parameters |
| [**listSolutionSecurityUsers**](SolutionApi.md#listSolutionSecurityUsers) | **GET** /organizations/{organization_id}/solutions/{solution_id}/security/users | List solution security users |
| [**listSolutions**](SolutionApi.md#listSolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions |
| [**updateSolution**](SolutionApi.md#updateSolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution |
| [**updateSolutionAccessControl**](SolutionApi.md#updateSolutionAccessControl) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id} | Update solution access control |
| [**updateSolutionDefaultSecurity**](SolutionApi.md#updateSolutionDefaultSecurity) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/security/default | Update solution default security |
| [**updateSolutionParameter**](SolutionApi.md#updateSolutionParameter) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/parameters/{parameter_id} | Update solution parameter |
| [**updateSolutionParameterGroup**](SolutionApi.md#updateSolutionParameterGroup) | **PATCH** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups/{parameter_group_id} | Update a solution parameter group |
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
- **Accept**: application/json, application/yaml

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
- **Accept**: application/json, application/yaml

<a name="createSolutionParameter"></a>
# **createSolutionParameter**
> RunTemplateParameter createSolutionParameter(organization\_id, solution\_id, RunTemplateParameterCreateRequest)

Create solution parameter for a solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **RunTemplateParameterCreateRequest** | [**RunTemplateParameterCreateRequest**](../Models/RunTemplateParameterCreateRequest.md)| Parameter to create | |

### Return type

[**RunTemplateParameter**](../Models/RunTemplateParameter.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="createSolutionParameterGroup"></a>
# **createSolutionParameterGroup**
> RunTemplateParameterGroup createSolutionParameterGroup(organization\_id, solution\_id, RunTemplateParameterGroupCreateRequest)

Create a solution parameter group

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **RunTemplateParameterGroupCreateRequest** | [**RunTemplateParameterGroupCreateRequest**](../Models/RunTemplateParameterGroupCreateRequest.md)| Parameter group to create | |

### Return type

[**RunTemplateParameterGroup**](../Models/RunTemplateParameterGroup.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

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

<a name="deleteSolutionParameter"></a>
# **deleteSolutionParameter**
> deleteSolutionParameter(organization\_id, solution\_id, parameter\_id)

Delete specific parameter from the solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **parameter\_id** | **String**| the solution parameter identifier | [default to null] |

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteSolutionParameterGroup"></a>
# **deleteSolutionParameterGroup**
> deleteSolutionParameterGroup(organization\_id, solution\_id, parameter\_group\_id)

Delete a parameter group from the solution

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **parameter\_group\_id** | **String**| the parameter group identifier | [default to null] |

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
- **Accept**: application/json, application/yaml

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
- **Accept**: application/json, application/yaml

<a name="getSolutionParameter"></a>
# **getSolutionParameter**
> RunTemplateParameter getSolutionParameter(organization\_id, solution\_id, parameter\_id)

Get the details of a solution parameter

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **parameter\_id** | **String**| the solution parameter identifier | [default to null] |

### Return type

[**RunTemplateParameter**](../Models/RunTemplateParameter.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="getSolutionParameterGroup"></a>
# **getSolutionParameterGroup**
> RunTemplateParameterGroup getSolutionParameterGroup(organization\_id, solution\_id, parameter\_group\_id)

Get details of a solution parameter group

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **parameter\_group\_id** | **String**| the parameter group identifier | [default to null] |

### Return type

[**RunTemplateParameterGroup**](../Models/RunTemplateParameterGroup.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

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
- **Accept**: application/json, application/yaml

<a name="listSolutionParameterGroups"></a>
# **listSolutionParameterGroups**
> List listSolutionParameterGroups(organization\_id, solution\_id)

List all solution parameter groups

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |

### Return type

[**List**](../Models/RunTemplateParameterGroup.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listSolutionParameters"></a>
# **listSolutionParameters**
> List listSolutionParameters(organization\_id, solution\_id)

List all solution parameters

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |

### Return type

[**List**](../Models/RunTemplateParameter.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

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
- **Accept**: application/json, application/yaml

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
- **Accept**: application/json, application/yaml

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
- **Accept**: application/json, application/yaml

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

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

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
- **Accept**: application/json, application/yaml

<a name="updateSolutionParameter"></a>
# **updateSolutionParameter**
> RunTemplateParameter updateSolutionParameter(organization\_id, solution\_id, parameter\_id, RunTemplateParameterUpdateRequest)

Update solution parameter

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **parameter\_id** | **String**| the solution parameter identifier | [default to null] |
| **RunTemplateParameterUpdateRequest** | [**RunTemplateParameterUpdateRequest**](../Models/RunTemplateParameterUpdateRequest.md)| Parameter to update | |

### Return type

[**RunTemplateParameter**](../Models/RunTemplateParameter.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

<a name="updateSolutionParameterGroup"></a>
# **updateSolutionParameterGroup**
> RunTemplateParameterGroup updateSolutionParameterGroup(organization\_id, solution\_id, parameter\_group\_id, RunTemplateParameterGroupUpdateRequest)

Update a solution parameter group

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organization\_id** | **String**| the Organization identifier | [default to null] |
| **solution\_id** | **String**| the Solution identifier | [default to null] |
| **parameter\_group\_id** | **String**| the parameter group identifier | [default to null] |
| **RunTemplateParameterGroupUpdateRequest** | [**RunTemplateParameterGroupUpdateRequest**](../Models/RunTemplateParameterGroupUpdateRequest.md)| Parameter groups to update | |

### Return type

[**RunTemplateParameterGroup**](../Models/RunTemplateParameterGroup.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

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
- **Accept**: application/json, application/yaml

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

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json, application/yaml

