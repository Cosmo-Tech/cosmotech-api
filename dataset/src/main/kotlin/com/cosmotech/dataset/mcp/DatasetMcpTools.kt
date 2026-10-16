// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.mcp

import com.cosmotech.common.mcp.Base64DecodedMultipartFile
import com.cosmotech.common.mcp.MAX_FILE_SIZE_BYTES
import com.cosmotech.common.mcp.MAX_FILE_SIZE_MB
import com.cosmotech.common.mcp.requireMaxFileSize
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
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
          "Create a new dataset in the workspace. The dataset is initialized with specified " +
              "parts (can be empty), each part carrying its base64 encoded file. " +
              "The dataset can also be populated later through dataset parts. " +
              "Returns the created dataset. File size limit: the total decoded size of all " +
              "part files must not exceed $MAX_FILE_SIZE_MB MiB ($MAX_FILE_SIZE_BYTES bytes).",
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
      @McpToolParam(
          description =
              "the Dataset parts to create with their files. Part sourceNames must be unique",
          required = false,
      )
      parts: List<DatasetPartMcpInput>?,
  ): Dataset {
    val datasetParts = parts.orEmpty()
    val files = datasetParts.map { Base64DecodedMultipartFile.of(it.sourceName, it.file) }
    requireMaxFileSize(files)

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = name,
            description = description,
            tags = tags?.toMutableList(),
            additionalData = additionalData?.toMutableMap(),
            parts = datasetParts.map { it.toDatasetPartCreateRequest() }.toMutableList(),
            security = security,
        )

    return datasetApiService.createDataset(
        organizationId,
        workspaceId,
        datasetCreateRequest,
        files.toTypedArray(),
    )
  }

  @McpTool(
      name = UPDATE_DATASET_TOOL,
      description =
          "Update dataset information and return the updated dataset. Omitted fields are left " +
              "unchanged. If parts is provided (even empty), ALL existing dataset parts and " +
              "their data are deleted and replaced by the given parts, each part carrying its " +
              "base64 encoded file. File size limit: the total decoded size of all part files " +
              "must not exceed $MAX_FILE_SIZE_MB MiB ($MAX_FILE_SIZE_BYTES bytes).",
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
      @McpToolParam(
          description =
              "the Dataset parts replacing all existing ones, with their files. " +
                  "Part sourceNames must be unique. Omit to keep existing parts",
          required = false,
      )
      parts: List<DatasetPartMcpInput>?,
  ): Dataset {
    val files = parts.orEmpty().map { Base64DecodedMultipartFile.of(it.sourceName, it.file) }
    requireMaxFileSize(files)

    val datasetUpdateRequest =
        DatasetUpdateRequest(
            name = name,
            description = description,
            tags = tags?.toMutableList(),
            additionalData = additionalData?.toMutableMap(),
            parts = parts?.map { it.toDatasetPartCreateRequest() }?.toMutableList(),
        )

    return datasetApiService.updateDataset(
        organizationId,
        workspaceId,
        datasetId,
        datasetUpdateRequest,
        files.toTypedArray(),
    )
  }
}

data class DatasetPartMcpInput(
    @field:McpToolParam(
        description =
            "Base64 encoded file content (RFC 4648, no data URI prefix). " +
                "Counts toward the $MAX_FILE_SIZE_MB MiB total size limit",
    )
    val file: String,
    @field:McpToolParam(description = "the Dataset Part name") val name: String,
    @field:McpToolParam(
        description =
            "the uploaded file name, including its extension (used for file type detection)",
    )
    val sourceName: String,
    @field:McpToolParam(description = "the Dataset Part description", required = false)
    val description: String? = null,
    @field:McpToolParam(description = "the list of tags", required = false)
    val tags: List<String>? = null,
    @field:McpToolParam(description = "Free form additional data", required = false)
    val additionalData: Map<String, Any>? = null,
    @field:McpToolParam(
        description =
            "'File' for file storage or 'DB' for database storage (CSV only). Defaults to 'File'",
        required = false,
    )
    val type: DatasetPartTypeEnum = DatasetPartTypeEnum.File,
) {
  fun toDatasetPartCreateRequest() =
      DatasetPartCreateRequest(
          name = name,
          sourceName = sourceName,
          description = description,
          tags = tags?.toMutableList(),
          additionalData = additionalData?.toMutableMap(),
          type = type,
      )
}
