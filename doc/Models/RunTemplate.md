# RunTemplate
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | the Solution Run Template id | [default to null]
**name** | **String** | the Run Template name | [optional] [default to null]
**description** | **String** | the Run Template description | [optional] [default to null]
**csmSimulation** | **String** | the Cosmo Tech simulation name. This information is send to the Engine. Mandatory information if no Engine is defined | [optional] [default to null]
**tags** | **List** | the list of Run Template tags | [optional] [default to null]
**computeSize** | **String** | the compute size needed for this Run Template. Standard sizes are basic and highcpu. Default is basic | [optional] [default to null]
**runSizing** | [**RunTemplateResourceSizing**](RunTemplateResourceSizing.md) |  | [optional] [default to null]
**noDataIngestionState** | **Boolean** | set to true if the run template does not want to check data ingestion state (no probes or not control plane) | [optional] [default to null]
**fetchDatasets** | **Boolean** | whether or not the fetch dataset step is done | [optional] [default to null]
**scenarioDataDownloadTransform** | **Boolean** | whether or not the scenario data download transform step step is done | [optional] [default to null]
**fetchScenarioParameters** | **Boolean** | whether or not the fetch parameters step is done | [optional] [default to null]
**applyParameters** | **Boolean** | whether or not the apply parameter step is done | [optional] [default to null]
**validateData** | **Boolean** | whether or not the validate step is done | [optional] [default to null]
**sendDatasetsToDataWarehouse** | **Boolean** | whether or not the Datasets values are send to the DataWarehouse prior to Simulation Run. If not set follow the Workspace setting | [optional] [default to null]
**sendInputParametersToDataWarehouse** | **Boolean** | whether or not the input parameters values are send to the DataWarehouse prior to Simulation Run. If not set follow the Workspace setting | [optional] [default to null]
**preRun** | **Boolean** | whether or not the pre-run step is done | [optional] [default to null]
**run** | **Boolean** | whether or not the run step is done | [optional] [default to null]
**postRun** | **Boolean** | whether or not the post-run step is done | [optional] [default to null]
**parametersJson** | **Boolean** | whether or not to store the scenario parameters in json instead of csv | [optional] [default to null]
**parametersHandlerSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**datasetValidatorSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**preRunSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**runSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**postRunSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**scenariodataTransformSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null]
**parameterGroups** | **List** | the ordered list of parameters groups for the Run Template | [optional] [default to null]
**stackSteps** | **Boolean** | whether or not to stack adjacent scenario run steps in one container run which will chain steps | [optional] [default to null]
**gitRepositoryUrl** | **String** | an optional URL to the git repository | [optional] [default to null]
**gitBranchName** | **String** | an optional git branch name | [optional] [default to null]
**runTemplateSourceDir** | **String** | an optional directory where to find the run template source | [optional] [default to null]
**orchestratorType** | [**RunTemplateOrchestrator**](RunTemplateOrchestrator.md) |  | [optional] [default to null]
**executionTimeout** | **Integer** | an optional duration in seconds in which a workflow is allowed to run | [optional] [default to null]
**deleteHistoricalData** | [**DeleteHistoricalData**](DeleteHistoricalData.md) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

