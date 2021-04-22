# RunTemplate
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | [**String**](string.md) | the Solution Run Template id | [default to null]
**name** | [**String**](string.md) | the Run Template name | [default to null]
**description** | [**String**](string.md) | the Run Template description | [optional] [default to null]
**csmSimulation** | [**String**](string.md) | the Cosmo Tech simulation name. This information is send to the Engine. Mandatory information if no Engine is defined | [optional] [default to null]
**tags** | [**List**](string.md) | the list of Run Template tags | [optional] [default to null]
**computeSize** | [**String**](string.md) | the compute size needed for this Run Template. Standard sizes are basic and highcpu. Default is basic | [optional] [default to null]
**parametersHandlerResource** | [**RunTemplateResourceStorage**](RunTemplateResourceStorage.md) |  | [optional] [default to null]
**datasetValidatorResource** | [**RunTemplateResourceStorage**](RunTemplateResourceStorage.md) |  | [optional] [default to null]
**preRunResource** | [**RunTemplateResourceStorage**](RunTemplateResourceStorage.md) |  | [optional] [default to null]
**engineResource** | [**RunTemplateResourceStorage**](RunTemplateResourceStorage.md) |  | [optional] [default to null]
**postRunResource** | [**RunTemplateResourceStorage**](RunTemplateResourceStorage.md) |  | [optional] [default to null]
**sendDatasetsToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the Datasets values are send to the DataWarehouse prior to Simulation Run | [optional] [default to true]
**sendInputParametersToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the input parameters values are send to the DataWarehouse prior to Simulation Run | [optional] [default to true]
**parameterGroups** | [**List**](string.md) | the ordered list of parameters groups for the Run Template | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

