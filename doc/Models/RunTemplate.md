# RunTemplate
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **id** | **String** | The Solution Run Template id | [default to null] |
| **name** | **String** | The Run Template name | [optional] [default to null] |
| **labels** | **Map** | A translated label with key as ISO 639-1 code | [optional] [default to null] |
| **description** | **String** | The Run Template description | [optional] [default to null] |
| **tags** | **List** | The list of Run Template tags | [optional] [default to null] |
| **computeSize** | **String** | The compute size used to select the node pool for this Run Template. When null, runs use the basic node pool and the stored value remains null.  | [optional] [default to null] |
| **runSizing** | [**RunTemplateResourceSizing**](RunTemplateResourceSizing.md) | Resource requests and limits for this Run Template. When null, run sizing falls back to Runner.runSizing, if set, or to the computeSize preset. The stored value remains null. Presets set both requests and limits: basic is 3 CPU and 4Gi memory, highcpu is 70 CPU and 130Gi memory, and highmemory is 7 CPU and 57Gi memory.  | [optional] [default to null] |
| **parameterGroups** | **List** | The ordered list of parameters groups for the Run Template | [default to null] |
| **executionTimeout** | **Integer** | Maximum workflow duration in seconds. When null, runs use the platform default of 28,800 seconds (8 hours); the stored value remains null.  | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

