# SolutionApi

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createSolution**](SolutionApi.md#createSolution) | **POST** /organizations/{organization_id}/solutions | Register a new solution
[**deleteSolution**](SolutionApi.md#deleteSolution) | **DELETE** /organizations/{organization_id}/solutions/{solution_id} | Delete a solution
[**findAllSolutions**](SolutionApi.md#findAllSolutions) | **GET** /organizations/{organization_id}/solutions | List all Solutions
[**findSolutionById**](SolutionApi.md#findSolutionById) | **GET** /organizations/{organization_id}/solutions/{solution_id} | Get the details of a solution
[**updateSolution**](SolutionApi.md#updateSolution) | **PATCH** /organizations/{organization_id}/solutions/{solution_id} | Update a solution
[**upload**](SolutionApi.md#upload) | **POST** /organizations/{organization_id}/solutions/upload | Upload and register a new solution


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

- **Content-Type**: application/json
- **Accept**: application/json

<a name="deleteSolution"></a>
# **deleteSolution**
> Solution deleteSolution(organization\_id, solution\_id)

Delete a solution

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

- **Content-Type**: application/json
- **Accept**: application/json

<a name="upload"></a>
# **upload**
> Solution upload(organization\_id, body)

Upload and register a new solution

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **body** | **File**| the Solution to upload and register |

### Return type

[**Solution**](../Models/Solution.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/yaml
- **Accept**: application/json

