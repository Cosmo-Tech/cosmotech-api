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
**fetchDatasets** | [**Boolean**](boolean.md) | whether or not the fetch dataset step is done | [optional] [default to null]
**fetchScenarioParameters** | [**Boolean**](boolean.md) | whether or not the fetch parameters step is done | [optional] [default to null]
**applyParameters** | [**Boolean**](boolean.md) | whether or not the apply parameter step is done | [optional] [default to null]
**validateData** | [**Boolean**](boolean.md) | whether or not the validate step is done | [optional] [default to null]
**sendDatasetsToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the Datasets values are send to the DataWarehouse prior to Simulation Run. If not set follow the Workspace setting | [optional] [default to null]
**sendInputParametersToDataWarehouse** | [**Boolean**](boolean.md) | whether or not the input parameters values are send to the DataWarehouse prior to Simulation Run. If not set follow the Workspace setting | [optional] [default to null]
**preRun** | [**Boolean**](boolean.md) | whether or not the pre-run step is done | [optional] [default to null]
**run** | [**Boolean**](boolean.md) | whether or not the run step is done | [optional] [default to null]
**postRun** | [**Boolean**](boolean.md) | whether or not the post-run step is done | [optional] [default to null]
**parametersHandlerSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**datasetValidatorSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**preRunSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**runSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**postRunSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**parameterGroups** | [**List**](string.md) | the ordered list of parameters groups for the Run Template | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

