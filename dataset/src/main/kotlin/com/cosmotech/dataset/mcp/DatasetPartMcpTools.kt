// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.mcp

import com.cosmotech.common.mcp.Base64DecodedMultipartFile
import com.cosmotech.common.mcp.MAX_FILE_SIZE_BYTES
import com.cosmotech.common.mcp.MAX_FILE_SIZE_MB
import com.cosmotech.common.mcp.requireMaxFileSize
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.dataset.domain.DatasetPartUpdateRequest
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

const val CREATE_DATASET_PART_TOOL = "create_dataset_part"
const val REPLACE_DATASET_PART_TOOL = "replace_dataset_part"

@Component
class DatasetPartMcpTools(private val datasetApiService: DatasetApiServiceInterface) {

  @McpTool(
      name = CREATE_DATASET_PART_TOOL,
      description =
          "Create a new data part within a dataset from a base64 encoded file. " +
              "Specify type as 'File' for file storage or 'DB' for " +
              "database storage (CSV only). Returns the created part. " +
              "File size limit: the decoded file must not exceed $MAX_FILE_SIZE_MB MiB " +
              "($MAX_FILE_SIZE_BYTES bytes).",
      annotations = McpTool.McpAnnotations(destructiveHint = false),
  )
  fun createDatasetPart(
      @McpToolParam(description = "the Organization identifier") organizationId: String,
      @McpToolParam(description = "the Workspace identifier") workspaceId: String,
      @McpToolParam(description = "the Dataset identifier") datasetId: String,
      @McpToolParam(
          description =
              "Base64 encoded file content (RFC 4648, no data URI prefix). " +
                  "Max decoded size is $MAX_FILE_SIZE_MB MiB ($MAX_FILE_SIZE_BYTES bytes)",
      )
      file: String,
      @McpToolParam(description = "the Dataset Part name") name: String,
      @McpToolParam(
          description =
              "the uploaded file name, including its extension (used for file type detection)",
      )
      sourceName: String,
      @McpToolParam(description = "the Dataset Part description", required = false)
      description: String?,
      @McpToolParam(description = "the list of tags", required = false) tags: List<String>?,
      @McpToolParam(description = "Free form additional data", required = false)
      additionalData: Map<String, Any>?,
      @McpToolParam(
          description =
              "'File' for file storage or 'DB' for database storage (CSV only). Defaults to 'File'",
          required = false,
      )
      type: DatasetPartTypeEnum?,
  ): DatasetPart {
    val multipartFile = Base64DecodedMultipartFile.of(sourceName, file)
    requireMaxFileSize(listOf(multipartFile))

    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = name,
            sourceName = sourceName,
            description = description,
            tags = tags?.toMutableList(),
            additionalData = additionalData?.toMutableMap(),
            type = type ?: DatasetPartTypeEnum.File,
        )

    return datasetApiService.createDatasetPart(
        organizationId,
        workspaceId,
        datasetId,
        multipartFile,
        datasetPartCreateRequest,
    )
  }

  @McpTool(
      name = REPLACE_DATASET_PART_TOOL,
      description =
          "Replace the data of an existing dataset part with a base64 encoded file, and " +
              "optionally update its metadata (omitted fields are left unchanged). " +
              "The part type (File or DB) cannot be changed. Returns the replaced part. " +
              "File size limit: the decoded file must not exceed $MAX_FILE_SIZE_MB MiB " +
              "($MAX_FILE_SIZE_BYTES bytes).",
  )
  fun replaceDatasetPart(
      @McpToolParam(description = "the Organization identifier") organizationId: String,
      @McpToolParam(description = "the Workspace identifier") workspaceId: String,
      @McpToolParam(description = "the Dataset identifier") datasetId: String,
      @McpToolParam(description = "the Dataset part identifier") datasetPartId: String,
      @McpToolParam(
          description =
              "Base64 encoded file content (RFC 4648, no data URI prefix). " +
                  "Max decoded size is $MAX_FILE_SIZE_MB MiB ($MAX_FILE_SIZE_BYTES bytes)",
      )
      file: String,
      @McpToolParam(
          description =
              "the new uploaded file name, including its extension (used for file type " +
                  "detection). Defaults to the current sourceName",
          required = false,
      )
      sourceName: String?,
      @McpToolParam(description = "the new Dataset Part description", required = false)
      description: String?,
      @McpToolParam(description = "the new list of tags", required = false) tags: List<String>?,
      @McpToolParam(description = "the new free form additional data", required = false)
      additionalData: Map<String, Any>?,
  ): DatasetPart {
    val fileName =
        sourceName
            ?: datasetApiService
                .getDatasetPart(organizationId, workspaceId, datasetId, datasetPartId)
                .sourceName
    val multipartFile = Base64DecodedMultipartFile.of(fileName, file)
    requireMaxFileSize(listOf(multipartFile))

    val datasetPartUpdateRequest =
        DatasetPartUpdateRequest(
            sourceName = sourceName,
            description = description,
            tags = tags?.toMutableList(),
            additionalData = additionalData?.toMutableMap(),
        )

    return datasetApiService.replaceDatasetPart(
        organizationId,
        workspaceId,
        datasetId,
        datasetPartId,
        multipartFile,
        datasetPartUpdateRequest,
    )
  }
}
