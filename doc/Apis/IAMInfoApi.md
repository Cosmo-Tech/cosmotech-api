# IAMInfoApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**listIAMGroups**](IAMInfoApi.md#listIAMGroups) | **GET** /iaminfo/groups | Get the list of all groups |
| [**listIAMMembers**](IAMInfoApi.md#listIAMMembers) | **GET** /iaminfo/members | Get ALL IAM members list |


<a name="listIAMGroups"></a>
# **listIAMGroups**
> List listIAMGroups()

Get the list of all groups

### Parameters
This endpoint does not need any parameter.

### Return type

**List**

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

<a name="listIAMMembers"></a>
# **listIAMMembers**
> Members listIAMMembers()

Get ALL IAM members list

### Parameters
This endpoint does not need any parameter.

### Return type

[**Members**](../Models/Members.md)

### Authorization

[oAuth2AuthCode](../README.md#oAuth2AuthCode)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json, application/yaml

