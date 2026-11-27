// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.mcp

import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetUpdateRequest
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

const val CREATE_DATASET_TOOL = "create_dataset"
const val UPDATE_DATASET_TOOL = "update_dataset"

@Component
class DatasetMcpTools(private val datasetApiService: DatasetApiServiceInterface) {

  @McpTool(
      name = CREATE_DATASET_TOOL,
      description =
          "Create a new dataset in the workspace. The dataset cannot be initialized with specified " +
              "parts (the dataset parts list will be empty)." +
              "Dataset part should be added/updated/replaced using " +
              "respectively create_dataset_part/update_dataset_part/replace_dataset_part tools.",
      annotations = McpTool.McpAnnotations(destructiveHint = false),
  )
  fun createDataset(
      @McpToolParam(description = "the Organization identifier") organizationId: String,
      @McpToolParam(description = "the Workspace identifier") workspaceId: String,
      @McpToolParam(description = "the Dataset name") name: String,
      @McpToolParam(description = "the Dataset description", required = false) description: String?,
      @McpToolParam(description = "the list of tags", required = false) tags: List<String>?,
      @McpToolParam(description = "Free form additional data", required = false)
      additionalData: Map<String, Any>?,
      @McpToolParam(description = "the Dataset security information", required = false)
      security: DatasetSecurity?,
  ): Dataset {

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = name,
            description = description,
            tags = tags?.toMutableList(),
            additionalData = additionalData?.toMutableMap(),
            security = security,
        )

    return datasetApiService.createDataset(
        organizationId,
        workspaceId,
        datasetCreateRequest,
        null,
    )
  }

  @McpTool(
      name = UPDATE_DATASET_TOOL,
      description =
          "Update dataset information and return the updated dataset. Omitted fields are left " +
              "unchanged. Dataset parts cannot be updated using this tools. They'll remain the same. " +
              "To modified them, use adequate tools like " +
              "create_dataset_part/update_dataset_part/replace_dataset_part/delete_dataset_part tools.",
  )
  fun updateDataset(
      @McpToolParam(description = "the Organization identifier") organizationId: String,
      @McpToolParam(description = "the Workspace identifier") workspaceId: String,
      @McpToolParam(description = "the Dataset identifier") datasetId: String,
      @McpToolParam(description = "the new Dataset name", required = false) name: String?,
      @McpToolParam(description = "the new Dataset description", required = false)
      description: String?,
      @McpToolParam(description = "the new list of tags", required = false) tags: List<String>?,
      @McpToolParam(description = "the new free form additional data", required = false)
      additionalData: Map<String, Any>?,
  ): Dataset {

    val datasetUpdateRequest =
        DatasetUpdateRequest(
            name = name,
            description = description,
            tags = tags?.toMutableList(),
            additionalData = additionalData?.toMutableMap(),
        )

    return datasetApiService.updateDataset(
        organizationId,
        workspaceId,
        datasetId,
        datasetUpdateRequest,
        null,
    )
  }
}
