# SolutionApi

All URIs are relative to *https://api.azure.cosmo-platform.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addOrReplaceParameterGroups**](SolutionApi.md#addOrReplaceParameterGroups) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Add Parameter Groups. Any item with the same ID will be overwritten
[**addOrReplaceParameters**](SolutionApi.md#addOrReplaceParameters) | **POST** /organizations/{organization_id}/solutions/{solution_id}/parameters | Add Parameters. Any item with the same ID will be overwritten
[**addOrReplaceRunTemplates**](SolutionApi.md#addOrReplaceRunTemplates) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Add Run Templates. Any item with the same ID will be overwritten
[**createSolution**](SolutionApi.md#createSolution) | **POST** /organizations/{organization_id}/solutions | Register a new solution
[**deleteSolution**](SolutionApi.md#deleteSolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution
[**findAllSolutions**](SolutionApi.md#findAllSolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions
[**findSolutionById**](SolutionApi.md#findSolutionById) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution
[**removeAllRunTemplates**](SolutionApi.md#removeAllRunTemplates) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/runTemplates | Remove all Run Templates from the Solution specified
[**removeAllSolutionParameterGroups**](SolutionApi.md#removeAllSolutionParameterGroups) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameterGroups | Remove all Parameter Groups from the Solution specified
[**removeAllSolutionParameters**](SolutionApi.md#removeAllSolutionParameters) | **DELETE** /organizations/{organization_id}/solutions/{solution_id}/parameters | Remove all Parameters from the Solution specified
[**updateSolution**](SolutionApi.md#updateSolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution
[**uploadRunTemplateHandler**](SolutionApi.md#uploadRunTemplateHandler) | **POST** /organizations/{organization_id}/solutions/{solution_id}/runtemplates/{run_template_id}/handlers/{handler_id}/upload | Upload a Run Template step handler zip file


<a name="addOrReplaceParameterGroups"></a>
# **addOrReplaceParameterGroups**
> List addOrReplaceParameterGroups(organization\_id, solution\_id, RunTemplateParameterGroup)

Add Parameter Groups. Any item with the same ID will be overwritten

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]
 **RunTemplateParameterGroup** | [**List**](../Models/RunTemplateParameterGroup.md)| the Parameter Groups |

### Return type

[**List**](../Models/RunTemplateParameterGroup.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="addOrReplaceParameters"></a>
# **addOrReplaceParameters**
> List addOrReplaceParameters(organization\_id, solution\_id, RunTemplateParameter)

Add Parameters. Any item with the same ID will be overwritten

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]
 **RunTemplateParameter** | [**List**](../Models/RunTemplateParameter.md)| the Parameters |

### Return type

[**List**](../Models/RunTemplateParameter.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="addOrReplaceRunTemplates"></a>
# **addOrReplaceRunTemplates**
> List addOrReplaceRunTemplates(organization\_id, solution\_id, RunTemplate)

Add Run Templates. Any item with the same ID will be overwritten

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]
 **RunTemplate** | [**List**](../Models/RunTemplate.md)| the Run Templates |

### Return type

[**List**](../Models/RunTemplate.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createSolution"></a>
# **createSolution**
> Solution createSolution(organization\_id, Solution)

Register a new solution

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **Solution** | [**Solution**](../Models/Solution.md)| the Solution to create |

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

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="findAllSolutions"></a>
# **findAllSolutions**
> List findAllSolutions(organization\_id)

List all Solutions

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

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

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]

### Return type

[**Solution**](../Models/Solution.md)

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

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]

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

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]

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

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="updateSolution"></a>
# **updateSolution**
> Solution updateSolution(organization\_id, solution\_id, Solution)

Update a solution

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]
 **Solution** | [**Solution**](../Models/Solution.md)| the new Solution details. |

### Return type

[**Solution**](../Models/Solution.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="uploadRunTemplateHandler"></a>
# **uploadRunTemplateHandler**
> uploadRunTemplateHandler(organization\_id, solution\_id, run\_template\_id, handler\_id, body)

Upload a Run Template step handler zip file

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **solution\_id** | **String**| the Solution identifier | [default to null]
 **run\_template\_id** | **String**| the Run Template identifier | [default to null]
 **handler\_id** | **String**| the Handler id identifier | [default to null] [enum: parameters_handler, validator, prerun, engine, postrun]
 **body** | **File**|  | [optional]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: image/zip
- **Accept**: Not defined

