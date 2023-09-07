# Dataset
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | the Dataset unique identifier | [optional] [default to null]
**name** | **String** | the Dataset name | [optional] [default to null]
**description** | **String** | the Dataset description | [optional] [default to null]
**ownerId** | **String** | the User id which own this Dataset | [optional] [default to null]
**organizationId** | **String** | the Organization Id related to this Dataset | [optional] [default to null]
**parentId** | **String** | the Dataset id which is the parent of this Dataset | [optional] [default to null]
**twingraphId** | **String** | the twin graph id | [optional] [default to null]
**main** | **Boolean** | is this the main dataset | [optional] [default to null]
**sourceType** | [**DatasetSourceType**](DatasetSourceType.md) |  | [optional] [default to null]
**source** | [**SourceInfo**](SourceInfo.md) |  | [optional] [default to null]
**status** | **String** | the Dataset status | [optional] [default to null]
**tags** | **List** | the list of tags | [optional] [default to null]
**connector** | [**Dataset_connector**](Dataset_connector.md) |  | [optional] [default to null]
**fragmentsIds** | **List** | the list of other Datasets ids to compose as fragments | [optional] [default to null]
**validatorId** | **String** | the validator id | [optional] [default to null]
**compatibility** | [**List**](DatasetCompatibility.md) | the list of compatible Solutions versions | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

