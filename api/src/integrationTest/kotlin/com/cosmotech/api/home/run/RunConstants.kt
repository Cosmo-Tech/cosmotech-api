// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.run

/**
 * Constant class that contains for Run endpoints:
 * - default payload (RequestContent) for API calls
 * - default error messages (Errors) returned by API
 */
object RunConstants {

  object RequestContent {

    val TAGS = mutableListOf("tag1", "tag2")
    const val DESCRIPTION = "this_is_a_description"
    const val PARAMETER_GROUP_ID = "parameterGroup1"
    const val RUN_TEMPLATE_NAME = "this_is_a_name"
    const val RUN_TEMPLATE_COMPUTE_SIZE = "this_is_a_compute_size"
    const val WORKSPACE_KEY = "workspaceKey"
    const val CSM_SIMULATION_RUN = "a_csm_simulation_run"
    const val CUSTOM_DATA_TABLE_NAME = "cd_my_table"
    const val CUSTOM_DATA_QUERY = "{\"query\": \"SELECT * FROM cd_my_table\"}"

    const val WORKFLOW_ID = "a_workflow_id"
    const val WORKFLOW_NAME = "a_workflow_name"
    const val WORKFLOW_PHASE = "Succeeded"
    const val WORKFLOW_PROGRESS = "progress"
    const val WORKFLOW_MESSAGE = "this_is_a_message"

    const val NODE_ID = "nodeId1"
    const val NODE_NAME = "nodeName"
    const val HOST_NODE_NAME = "hostNodeName"
    const val NODE_MESSAGE = "this_is_a_message"
    const val NODE_PHASE = "Succeeded"
    const val NODE_PROGRESS = "progress"

    val DATASET_LIST = mutableListOf("datasetId1")

    const val RUN_TEMPLATE_PARAMETER_ID = "parameterId1"
    const val RUN_TEMPLATE_VALUE = "this_is_a_value"
    const val RUN_TEMPLATE_VAR_TYPE = "this_is_a_vartype"
    const val NODE_LABEL = "this_is_a_nodeLabel"

    const val CONTAINER_NAME = "containerName"
    const val CONTAINER_ID = "containerId"
    const val CONTAINER_IMAGE = "containerImage"
    const val CONTAINER_ENTRYPOINT = "this_is_an_entrypoint"
    const val CONTAINER_NODE_LABEL = "this_is_a_nodeLabel_too"
    val CONTAINER_LABELS = mutableMapOf("fr" to "this_is_a_label")
    val CONTAINER_ENV_VARS = mutableMapOf("envvar1" to "envvar1_value")
    val CONTAINER_RUN_ARGS = mutableListOf("runArgs1", "runArgs2")
    val CONTAINER_DEPENDENCIES = mutableListOf("dependency1", "dependency2")

    val PARAMETER_LABELS = mutableMapOf("fr" to "this_is_a_label")
  }

  object Errors {}
}
