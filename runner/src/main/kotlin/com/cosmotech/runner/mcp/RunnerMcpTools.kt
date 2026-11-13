// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.mcp

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.runner.RunnerApiServiceInterface
import io.awspring.cloud.s3.S3Exception
import io.awspring.cloud.s3.S3Template
import java.io.InputStream
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.slf4j.LoggerFactory
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

const val LIST_OUTPUT_FILES_TOOL = "list_output_file_names"
const val DOWNLOAD_OUTPUT_FILE_TOOL = "download_output_file"

private const val S3_LISTING_OUTPUT_FILES_ERROR_MESSAGE =
    "Something wrong happened when listing output files"

@Suppress("TooGenericExceptionCaught")
@Component
class RunnerMcpTools(
    private val runnerApiService: RunnerApiServiceInterface,
    private val s3Template: S3Template,
    private val s3Client: S3Client,
    private val csmPlatformProperties: CsmPlatformProperties,
) {
  private val logger = LoggerFactory.getLogger(RunnerMcpTools::class.java)

  @McpTool(
      name = LIST_OUTPUT_FILES_TOOL,
      description =
          "List all files containing run results (output file data). " +
              "The files are basic CSV files. " +
              "The connected user should have access to the runner to see the list. " +
              "The list is empty if the user do not have sufficient permissions. " +
              "Returns the list of all output file names.",
      annotations = McpTool.McpAnnotations(destructiveHint = false),
  )
  fun listOutputFileNames(
      @McpToolParam(description = "the Organization identifier") organizationId: String,
      @McpToolParam(description = "the Workspace identifier") workspaceId: String,
      @McpToolParam(description = "the Runner identifier") runnerId: String,
  ): List<String> {
    val result = emptyList<String>()
    try {
      runnerApiService.getRunner(organizationId, workspaceId, runnerId)
      val listObjectsRequest =
          ListObjectsV2Request.builder()
              .bucket(csmPlatformProperties.s3.bucketName)
              .prefix("$runnerId/")
              .build()

      try {
        return s3Client
            .listObjectsV2Paginator(listObjectsRequest)
            .stream()
            .flatMap { s3Object ->
              s3Object.contents().stream().map {
                it.key().removePrefix("$runnerId/")
              }
            }
            .toList()
      } catch (e: AwsServiceException) {
        logger.error(S3_LISTING_OUTPUT_FILES_ERROR_MESSAGE, e)
      } catch (e: SdkClientException) {
        logger.error(S3_LISTING_OUTPUT_FILES_ERROR_MESSAGE, e)
      } catch (e: S3Exception) {
        logger.error(S3_LISTING_OUTPUT_FILES_ERROR_MESSAGE, e)
      }
    } catch (e: Exception) {
      logger.error(S3_LISTING_OUTPUT_FILES_ERROR_MESSAGE, e)
    }

    return result
  }

  @McpTool(
      name = DOWNLOAD_OUTPUT_FILE_TOOL,
      description =
          "Download a range of data lines of a specific output file data (probe). " +
              "The output file data is a basic CSV file. " +
              "The connected user should have access to the runner to download a file. " +
              "'..' is not allowed in output file name  (security issue). " +
              "Lines are 1-based, inclusive and do not count the header line. " +
              "The CSV header is always returned as the first line of the response.",
  )
  fun downloadOutputFile(
      @McpToolParam(description = "the Organization identifier") organizationId: String,
      @McpToolParam(description = "the Workspace identifier") workspaceId: String,
      @McpToolParam(description = "the Runner identifier") runnerId: String,
      @McpToolParam(description = "the output file name") outputFileName: String,
      @McpToolParam(description = "the first data line to return (1-based, header excluded)")
      startLine: Int,
      @McpToolParam(description = "the last data line to return (inclusive, header excluded)")
      endLine: Int,
  ): String {
    require(".." !in outputFileName) { "Invalid filename: '$outputFileName'. '..' is not allowed" }
    require(startLine >= 1) { "Invalid startLine: '$startLine'. It must be greater than 0" }
    require(endLine >= startLine) {
      "Invalid endLine: '$endLine'. It must be greater than or equal to startLine ($startLine)"
    }
    runnerApiService.getRunner(organizationId, workspaceId, runnerId)

    val inputStream: InputStream
    try {
      inputStream =
          s3Template
              .download(
                  csmPlatformProperties.s3.bucketName,
                  "${runnerId}/$outputFileName",
              )
              .inputStream
    } catch (exception: NoSuchKeyException) {
      throw CsmResourceNotFoundException("$outputFileName does not exist.", exception)
    }

    val readFormat = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()

    inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
      val parser = readFormat.parse(reader)
      val writeFormat =
          CSVFormat.DEFAULT.builder().setHeader(*parser.headerNames.toTypedArray()).get()
      val output = StringBuilder()
      CSVPrinter(output, writeFormat).use { printer ->
        parser.asSequence().drop(startLine - 1).take(endLine - startLine + 1).forEach {
          printer.printRecord(it)
        }
      }
      return output.toString()
    }
  }
}
