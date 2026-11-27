// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.mcp

import com.cosmotech.common.mcp.Base64DecodedMultipartFile
import com.cosmotech.common.mcp.MAX_FILE_SIZE_BYTES
import com.cosmotech.common.mcp.MAX_FILE_SIZE_MB
import com.cosmotech.common.mcp.requireMaxFileSize
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.WorkspaceFile
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

const val CREATE_WORKSPACE_FILE_TOOL = "create_workspace_file"

@Component
class WorkspaceFileMcpTools(private val workspaceApiService: WorkspaceApiServiceInterface) {

  @McpTool(
      name = CREATE_WORKSPACE_FILE_TOOL,
      description =
          "Upload a base64 encoded file to workspace storage. Use 'destination' to specify " +
              "path, 'overwrite' to replace existing files. Returns the uploaded file details. " +
              "File size limit: the decoded file must not exceed $MAX_FILE_SIZE_MB MiB " +
              "($MAX_FILE_SIZE_BYTES bytes).",
  )
  fun createWorkspaceFile(
      @McpToolParam(description = "the Organization identifier") organizationId: String,
      @McpToolParam(description = "the Workspace identifier") workspaceId: String,
      @McpToolParam(
          description =
              "Base64 encoded file content (RFC 4648, no data URI prefix). " +
                  "Max decoded size is $MAX_FILE_SIZE_MB MiB ($MAX_FILE_SIZE_BYTES bytes)",
      )
      file: String,
      @McpToolParam(
          description =
              "the uploaded file name, including its extension (used for file type detection)",
      )
      fileName: String,
      @McpToolParam(
          description = "Set to true to replace an existing file. Defaults to false",
          required = false,
      )
      overwrite: Boolean?,
      @McpToolParam(
          description =
              "Destination path, relative to the Workspace root. If it ends with '/', it is " +
                  "treated as a directory and fileName is appended. Defaults to fileName",
          required = false,
      )
      destination: String?,
  ): WorkspaceFile {
    val multipartFile = Base64DecodedMultipartFile.of(fileName, file)
    requireMaxFileSize(listOf(multipartFile))

    return workspaceApiService.createWorkspaceFile(
        organizationId,
        workspaceId,
        multipartFile,
        overwrite ?: false,
        destination,
    )
  }
}
