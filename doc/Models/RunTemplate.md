# RunTemplate
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | The Solution Run Template id | [default to null] |
| **name** | **String** | The Run Template name | [optional] [default to null] |
| **labels** | **Map** | a translated label with key as ISO 639-1 code | [optional] [default to null] |
| **description** | **String** | The Run Template description | [optional] [default to null] |
| **csmSimulation** | **String** | The Cosmo Tech simulation name | [optional] [default to null] |
| **tags** | **List** | The list of Run Template tags | [optional] [default to null] |
| **computeSize** | **String** | The compute size needed for this Run Template | [optional] [default to null] |
| **runSizing** | [**RunTemplateResourceSizing**](RunTemplateResourceSizing.md) |  | [optional] [default to null] |
| **noDataIngestionState** | **Boolean** | Set to true if the run template does not want to check data ingestion state | [optional] [default to null] |
| **parametersHandlerSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null] |
| **datasetValidatorSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null] |
| **preRunSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null] |
| **runSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null] |
| **postRunSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null] |
| **scenariodataTransformSource** | [**RunTemplateStepSource**](RunTemplateStepSource.md) |  | [optional] [default to null] |
| **parameterGroups** | **List** | The ordered list of parameters groups for the Run Template | [optional] [default to null] |
| **stackSteps** | **Boolean** | Whether or not to stack adjacent scenario run steps | [optional] [default to null] |
| **gitRepositoryUrl** | **String** | An optional URL to the git repository | [optional] [default to null] |
| **gitBranchName** | **String** | An optional git branch name | [optional] [default to null] |
| **runTemplateSourceDir** | **String** | An optional directory where to find the run template source | [optional] [default to null] |
| **executionTimeout** | **Integer** | An optional duration in seconds in which a workflow is allowed to run | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

