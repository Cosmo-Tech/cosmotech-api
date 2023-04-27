# Scenario
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | the Scenario unique identifier | [optional] [default to null]
**name** | **String** | the Scenario name | [optional] [default to null]
**description** | **String** | the Scenario description | [optional] [default to null]
**tags** | **List** | the list of tags | [optional] [default to null]
**parentId** | **String** | the Scenario parent id | [optional] [default to null]
**ownerId** | **String** | the user id which own this Scenario | [optional] [default to null]
**rootId** | **String** | the scenario root id | [optional] [default to null]
**solutionId** | **String** | the Solution Id associated with this Scenario | [optional] [default to null]
**runTemplateId** | **String** | the Solution Run Template Id associated with this Scenario | [optional] [default to null]
**organizationId** | **String** | the associated Organization Id | [optional] [default to null]
**workspaceId** | **String** | the associated Workspace Id | [optional] [default to null]
**state** | [**ScenarioJobState**](ScenarioJobState.md) |  | [optional] [default to null]
**creationDate** | **Long** | the Scenario creation date | [optional] [default to null]
**lastUpdate** | **Long** | the last time a Scenario was updated | [optional] [default to null]
**ownerName** | **String** | the name of the owner | [optional] [default to null]
**solutionName** | **String** | the Solution name | [optional] [default to null]
**runTemplateName** | **String** | the Solution Run Template name associated with this Scenario | [optional] [default to null]
**datasetList** | **List** | the list of Dataset Id associated to this Scenario Run Template | [optional] [default to null]
**runSizing** | [**ScenarioResourceSizing**](ScenarioResourceSizing.md) |  | [optional] [default to null]
**parametersValues** | [**List**](ScenarioRunTemplateParameterValue.md) | the list of Solution Run Template parameters values | [optional] [default to null]
**lastRun** | [**ScenarioLastRun**](ScenarioLastRun.md) |  | [optional] [default to null]
**parentLastRun** | [**ScenarioLastRun**](ScenarioLastRun.md) |  | [optional] [default to null]
**rootLastRun** | [**ScenarioLastRun**](ScenarioLastRun.md) |  | [optional] [default to null]
**validationStatus** | [**ScenarioValidationStatus**](ScenarioValidationStatus.md) |  | [optional] [default to null]
**security** | [**ScenarioSecurity**](ScenarioSecurity.md) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

