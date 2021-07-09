# ValidatorApi

All URIs are relative to *https://api.cosmotech.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createValidator**](ValidatorApi.md#createValidator) | **POST** /organizations/{organization_id}/datasets/validators | Register a new validator
[**createValidatorRun**](ValidatorApi.md#createValidatorRun) | **POST** /organizations/{organization_id}/datasets/validators/{validator_id}/history | Register a new validator run
[**deleteValidator**](ValidatorApi.md#deleteValidator) | **DELETE** /organizations/{organization_id}/datasets/validators/{validator_id} | Delete a validator
[**deleteValidatorRun**](ValidatorApi.md#deleteValidatorRun) | **DELETE** /organizations/{organization_id}/datasets/validators/{validator_id}/history/{validatorrun_id} | Delete a validator run
[**findAllValidatorRuns**](ValidatorApi.md#findAllValidatorRuns) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id}/history | List all Validator Runs
[**findAllValidators**](ValidatorApi.md#findAllValidators) | **GET** /organizations/{organization_id}/datasets/validators | List all Validators
[**findValidatorById**](ValidatorApi.md#findValidatorById) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id} | Get the details of a validator
[**findValidatorRunById**](ValidatorApi.md#findValidatorRunById) | **GET** /organizations/{organization_id}/datasets/validators/{validator_id}/history/{validatorrun_id} | Get the details of a validator run
[**runValidator**](ValidatorApi.md#runValidator) | **POST** /organizations/{organization_id}/datasets/validators/{validator_id}/run | Run a Validator


<a name="createValidator"></a>
# **createValidator**
> Validator createValidator(organization\_id, Validator)

Register a new validator

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **Validator** | [**Validator**](../Models/Validator.md)| the Validator to create |

### Return type

[**Validator**](../Models/Validator.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="createValidatorRun"></a>
# **createValidatorRun**
> ValidatorRun createValidatorRun(organization\_id, validator\_id, ValidatorRun)

Register a new validator run

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **validator\_id** | **String**| the ValidatorRun identifier | [default to null]
 **ValidatorRun** | [**ValidatorRun**](../Models/ValidatorRun.md)| the Validator Run to create |

### Return type

[**ValidatorRun**](../Models/ValidatorRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

<a name="deleteValidator"></a>
# **deleteValidator**
> deleteValidator(organization\_id, validator\_id)

Delete a validator

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **validator\_id** | **String**| the Validator identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="deleteValidatorRun"></a>
# **deleteValidatorRun**
> deleteValidatorRun(organization\_id, validator\_id, validatorrun\_id)

Delete a validator run

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **validator\_id** | **String**| the Validator identifier | [default to null]
 **validatorrun\_id** | **String**| the Validator Run identifier | [default to null]

### Return type

null (empty response body)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="findAllValidatorRuns"></a>
# **findAllValidatorRuns**
> List findAllValidatorRuns(organization\_id, validator\_id)

List all Validator Runs

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **validator\_id** | **String**| the ValidatorRun identifier | [default to null]

### Return type

[**List**](../Models/ValidatorRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findAllValidators"></a>
# **findAllValidators**
> List findAllValidators(organization\_id)

List all Validators

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]

### Return type

[**List**](../Models/Validator.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findValidatorById"></a>
# **findValidatorById**
> Validator findValidatorById(organization\_id, validator\_id)

Get the details of a validator

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **validator\_id** | **String**| the Validator identifier | [default to null]

### Return type

[**Validator**](../Models/Validator.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="findValidatorRunById"></a>
# **findValidatorRunById**
> ValidatorRun findValidatorRunById(organization\_id, validator\_id, validatorrun\_id)

Get the details of a validator run

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **validator\_id** | **String**| the Validator identifier | [default to null]
 **validatorrun\_id** | **String**| the Validator Run identifier | [default to null]

### Return type

[**ValidatorRun**](../Models/ValidatorRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="runValidator"></a>
# **runValidator**
> ValidatorRun runValidator(organization\_id, validator\_id, ValidatorRun)

Run a Validator

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organization\_id** | **String**| the Organization identifier | [default to null]
 **validator\_id** | **String**| the ValidatorRun identifier | [default to null]
 **ValidatorRun** | [**ValidatorRun**](../Models/ValidatorRun.md)| the Validator to run |

### Return type

[**ValidatorRun**](../Models/ValidatorRun.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: application/json, application/yaml
- **Accept**: application/json

