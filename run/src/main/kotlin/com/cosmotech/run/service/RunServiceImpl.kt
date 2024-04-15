// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.RunStart
import com.cosmotech.api.events.RunStop
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.run.RunApiServiceInterface
import com.cosmotech.run.RunContainerFactory
import com.cosmotech.run.config.createDB
import com.cosmotech.run.config.dropDB
import com.cosmotech.run.config.existTable
import com.cosmotech.run.config.toDataTableName
import com.cosmotech.run.container.StartInfo
import com.cosmotech.run.domain.QueryResult
import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunData
import com.cosmotech.run.domain.RunDataQuery
import com.cosmotech.run.domain.RunLogs
import com.cosmotech.run.domain.RunState
import com.cosmotech.run.domain.RunStatus
import com.cosmotech.run.domain.RunTemplateParameterValue
import com.cosmotech.run.domain.SendRunDataRequest
import com.cosmotech.run.repository.RunRepository
import com.cosmotech.run.utils.isTerminal
import com.cosmotech.run.utils.withoutSensitiveData
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.runner.RunnerApiServiceInterface
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.service.getRbac
import com.google.gson.Gson
import java.sql.SQLException
import java.time.Instant
import org.apache.commons.lang3.NotImplementedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.stereotype.Service

internal const val WORKFLOW_TYPE_RUN = "container-run"

@Service
@Suppress("TooManyFunctions")
class RunServiceImpl(
    private val containerFactory: RunContainerFactory,
    private val workflowService: WorkflowService,
    private val runnerApiService: RunnerApiServiceInterface,
    private val runRepository: RunRepository,
    private val csmRbac: CsmRbac,
    private val adminRunStorageTemplate: JdbcTemplate
) : CsmPhoenixService(), RunApiServiceInterface {

  @Value("\${csm.platform.storage.admin.username}")
  private lateinit var adminStorageUsername: String

  @Value("\${csm.platform.storage.admin.password}")
  private lateinit var adminStoragePassword: String

  @Value("\${csm.platform.storage.reader.username}")
  private lateinit var readerStorageUsername: String

  @Value("\${csm.platform.storage.reader.password}")
  private lateinit var readerStoragePassword: String

  @Value("\${csm.platform.storage.port}") private lateinit var port: String

  private val notImplementedExceptionMessage =
      "The API is configured to use the external result data service. " +
          "This endpoint is deactivated so, use scenario/scenariorun endpoints instead. " +
          "To change that, set the API configuration entry 'csm.platform.use-internal-result-services' to true"

  override fun listRuns(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      page: Int?,
      size: Int?
  ): List<Run> {
    // This call verify the user read authorization in the Runner
    runnerApiService.getRunner(organizationId, workspaceId, runnerId)

    val defaultPageSize = csmPlatformProperties.twincache.scenariorun.defaultPageSize
    val pageable =
        constructPageRequest(page, size, defaultPageSize) ?: PageRequest.of(0, defaultPageSize)

    return runRepository
        .findByRunnerId(organizationId, workspaceId, runnerId, pageable)
        .toList()
        .map { it.withStateInformation().withoutSensitiveData() }
  }

  @Suppress("MagicNumber", "LongMethod")
  private fun sendDataToStorage(
      runId: String,
      tableName: String,
      data: List<Map<String, Any>>,
      isProbeData: Boolean = false
  ): RunData {
    val runtimeDS =
        DriverManagerDataSource(
            "jdbc:postgresql://localhost:$port/$runId", adminStorageUsername, adminStoragePassword)
    runtimeDS.setDriverClassName("org.postgresql.Driver")

    val runDBJdbcTemplate = JdbcTemplate(runtimeDS)

    var keys: Set<String> = setOf()
    val keyTypeMap = mutableMapOf<String, String>()
    val keyTypeWeight = mutableMapOf<String, Int>()

    val jsonTypeMapWeight =
        mapOf(
            "Boolean" to 0,
            "Int" to 1,
            "Double" to 1,
            "String" to 2,
            "ArrayList" to 3,
            "LinkedHashMap" to 3)
    val conflictKeyWeight = 3

    data.forEach { dataLine ->
      keys = keys union dataLine.keys
      dataLine.keys.forEach { key ->
        // Get weight for a given column
        val keyWeight = jsonTypeMapWeight[dataLine[key]!!::class.simpleName]
        if (!keyTypeWeight.containsKey(key)) keyTypeWeight[key] = keyWeight!!
        // If a conflict exists between 2 values in a same column use default value instead
        else if (keyTypeWeight[key] != keyWeight) keyTypeWeight[key] = conflictKeyWeight
      }
    }

    val postgresTypeFromWeight = mapOf(0 to "BOOLEAN", 1 to "NUMERIC", 2 to "TEXT", 3 to "JSONB")
    val postgresTypeToWeight = mapOf("BOOLEAN" to 0, "NUMERIC" to 1, "TEXT" to 2, "JSONB" to 3)
    // Convert all weight from columns to actual postgresql types
    keyTypeWeight.forEach { (key, weight) -> keyTypeMap[key] = postgresTypeFromWeight[weight]!! }

    val gson = Gson()

    val connection = runDBJdbcTemplate.dataSource!!.connection

    val dataTableName = tableName.toDataTableName(isProbeData)

    try {
      connection.autoCommit = false
      if (!runDBJdbcTemplate.existTable(tableName, isProbeData)) {

        // Create list of typed columns
        val keyTypeList = keys.joinToString(separator = ", ") { "\"$it\" ${keyTypeMap[it]!!}" }

        // Make use of a prepared statement to send the create table query
        val createTablePreparedStatement =
            connection.prepareStatement("CREATE TABLE \"$dataTableName\" ( $keyTypeList ) ")
        logger.debug("Creating new table $dataTableName for run $runId")

        createTablePreparedStatement.executeUpdate()

        // Grant select rights to the reader account
        val grantSelectPreparedStatement =
            connection.prepareStatement(
                "GRANT SELECT ON TABLE \"$dataTableName\" TO $readerStorageUsername ")
        logger.debug(
            "Granted SELECT to user $readerStorageUsername on table $dataTableName for run $runId")
        grantSelectPreparedStatement.executeUpdate()
      } else {
        // The table exist already, first get existing columns with their type
        val listColumnsPreparedStatement =
            connection.prepareStatement(
                "SELECT column_name, upper(data_type::text) as column_type " +
                    "FROM information_schema.columns WHERE table_name = ?")
        listColumnsPreparedStatement.setString(1, dataTableName)
        val listKeyResultSet = listColumnsPreparedStatement.executeQuery()
        var existingKeys: Set<String> = setOf()
        val existingKeysWeight = mutableMapOf<String, Int>()
        val existingKeysType = mutableMapOf<String, String>()
        // Loop through the results to get effective types of each column mapped to a weight as done on the data
        while (listKeyResultSet.next()) {
          val key = listKeyResultSet.getString("column_name")
          val keyType = listKeyResultSet.getString("column_type")
          val keyWeight = postgresTypeToWeight[keyType]!!
          existingKeysWeight[key] = keyWeight
          existingKeysType[key] = keyType
          existingKeys = existingKeys union setOf(key)
        }

        val missingKeys = keys - existingKeys
        val commonKeys = keys - missingKeys
        // For each column both in data and existing table make a type check
        commonKeys.forEach { commonKey ->
          val toSendKeyWeight = keyTypeWeight[commonKey]!!
          val effectiveKeyWeight = existingKeysWeight[commonKey]!!
          // If the existing type in the database is not compatible with the type of the data throw an error
          if (effectiveKeyWeight != conflictKeyWeight && toSendKeyWeight != effectiveKeyWeight)
              throw SQLException(
                  "Column $commonKey can not be converted to ${existingKeysType[commonKey]!!}")
        }
        missingKeys.forEach { missingKey ->
          // Alter the table for each missing key to add the column missing
          val alterTablePreparedStatement =
              connection.prepareStatement(
                  "ALTER TABLE \"$dataTableName\" ADD COLUMN \"$missingKey\" ${keyTypeMap[missingKey]!!}")
          logger.debug("Adding COLUMN $missingKey on table $dataTableName for run $runId")
          alterTablePreparedStatement.executeUpdate()
        }
      }
      data.forEach { dataLine ->
        // Insertion of data using prepared statements
        // for each key a parameter is created in the SQL query
        // that is then replaced by a string representation of the data that is then cast to the
        // correct type
        val lineKeys = dataLine.keys.joinToString(separator = ", ") { "\"$it\"" }
        // Make use of the key map to cast each value to the correct data type
        val lineData = dataLine.keys.joinToString(separator = ", ") { "?::${keyTypeMap[it]!!}" }

        val insertPreparedStatement =
            connection.prepareStatement(
                "INSERT INTO \"$dataTableName\" ( $lineKeys ) VALUES ( $lineData ) ")
        var position = 0

        for (key in dataLine.keys) {
          // insert all values as pure data into the statement ensuring no SQL can be executed inside the query
          position += 1
          insertPreparedStatement.setString(position, gson.toJson(dataLine[key]))
        }
        insertPreparedStatement.executeUpdate()
      }

      logger.debug("Inserted ${data.size} rows in table $dataTableName for run $runId")
      connection.commit()
    } catch (e: SQLException) {
      connection.rollback()
      throw e
    }
    return RunData(
        databaseName = runId, tableName = tableName.toDataTableName(isProbeData), data = data)
  }

  override fun sendRunData(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String,
      sendRunDataRequest: SendRunDataRequest
  ): RunData {

    checkInternalResultDataServiceConfiguration()
    val run = getRun(organizationId, workspaceId, runnerId, runId)
    run.hasPermission(PERMISSION_WRITE)

    return this.sendDataToStorage(runId, sendRunDataRequest.id!!, sendRunDataRequest.data!!)
  }

  override fun queryRunData(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String,
      runDataQuery: RunDataQuery
  ): QueryResult {
    val runtimeDS =
        DriverManagerDataSource(
            "jdbc:postgresql://localhost:$port/$runId",
            readerStorageUsername,
            readerStoragePassword)
    runtimeDS.setDriverClassName("org.postgresql.Driver")

    val runDBJdbcTemplate = JdbcTemplate(runtimeDS)

    val result = runDBJdbcTemplate.queryForList(runDataQuery.query)
    val resultList = mutableListOf(String())
    result.forEach { resultList.add(it.toString()) }

    return QueryResult(resultList)
  }

  private fun Run.withStateInformation(): Run {
    if (this.state?.isTerminal() == true) {
      return this
    }

    val state = getRunStatus(this).state
    val run = this.copy(state = state)

    if (state?.isTerminal() == true) {
      runRepository.save(run)
    }
    return run
  }

  private fun getRunStatus(run: Run): RunStatus {
    val runStatus = this.workflowService.getRunStatus(run)
    return runStatus.copy(
        state = mapWorkflowPhaseToRunStatus(phase = runStatus.phase, runId = run.id))
  }

  private fun mapWorkflowPhaseToRunStatus(phase: String?, runId: String?): RunState {
    return when (phase) {
      "Pending",
      "Running" -> RunState.Running
      "Succeeded" -> RunState.Successful
      "Skipped",
      "Failed",
      "Error",
      "Omitted" -> RunState.Failed
      else -> {
        logger.warn("Unhandled state response for job {}: {} => Returning Unknown", runId, phase)
        RunState.Unknown
      }
    }
  }

  override fun getRun(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): Run {
    runnerApiService.getRunner(organizationId, workspaceId, runnerId)
    val run =
        runRepository
            .findBy(organizationId, workspaceId, runnerId, runId)
            .orElseThrow {
              throw IllegalArgumentException(
                  "Run #$runId not found in #$runnerId. In #$workspaceId, #$organizationId.")
            }
            .withStateInformation()
            .withoutSensitiveData()

    run.hasPermission(PERMISSION_READ)
    return run
  }

  override fun deleteRun(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ) {
    val run = this.getRun(organizationId, workspaceId, runnerId, runId)
    run.hasPermission(PERMISSION_DELETE)
    try {
      runRepository.delete(run)
      adminRunStorageTemplate.dropDB(runId)
    } catch (exception: IllegalStateException) {
      logger.debug(
          "An error occured while deleteting Run {}: {}", run.id, exception.message, exception)
    }
  }

  override fun getRunLogs(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): RunLogs {
    return workflowService.getRunLogs(getRun(organizationId, workspaceId, runnerId, runId))
  }

  override fun getRunStatus(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): RunStatus {
    return getRunStatus(this.getRun(organizationId, workspaceId, runnerId, runId))
  }

  @EventListener(RunStart::class)
  fun onRunStart(runStartRequest: RunStart) {
    val runner = runStartRequest.runnerData as Runner
    val runId = idGenerator.generate("run", prependPrefix = "run-")

    val startInfo =
        containerFactory.getStartInfo(
            runner.organizationId!!, runner.workspaceId!!, runner.id!!, WORKFLOW_TYPE_RUN, runId)
    val runRequest =
        workflowService.launchRun(
            startInfo.startContainers,
            startInfo.runTemplate.executionTimeout,
            startInfo.solution.alwaysPull ?: false)
    val run = this.dbCreateRun(runId, runner, startInfo, runRequest)

    adminRunStorageTemplate.createDB(runId, readerStorageUsername)

    runStartRequest.response = run.id
  }

  private fun dbCreateRun(
      runId: String,
      runner: Runner,
      startInfo: StartInfo,
      runRequest: Run
  ): Run {
    val now = Instant.now().toString()
    var run =
        runRequest.copy(
            id = runId,
            ownerId = runner.ownerId,
            organizationId = runner.organizationId,
            workspaceId = runner.workspaceId,
            runnerId = runner.id,
            solutionId = startInfo.solution.id,
            runTemplateId = startInfo.runTemplate.id,
            generateName = startInfo.startContainers.generateName,
            computeSize = startInfo.runTemplate.computeSize,
            datasetList = runner.datasetList,
            createdAt = now,
            parametersValues =
                (runner.parametersValues?.map {
                      RunTemplateParameterValue(
                          parameterId = it.parameterId, varType = it.varType, value = it.value)
                    })
                    ?.toList(),
            nodeLabel = startInfo.startContainers.nodeLabel,
            containers = startInfo.startContainers.containers,
        )
    return runRepository.save(run)
  }

  @EventListener(RunStop::class)
  fun onRunStop(runStopRequest: RunStop) {
    val runner = runStopRequest.runnerData as Runner
    val run =
        getRun(
            runner.organizationId!!,
            runner.workspaceId!!,
            runner.id!!,
            runner.lastRun!!.runnerRunId!!)
    run.hasPermission(PERMISSION_WRITE)
    if (run.state!!.isTerminal()) {
      throw IllegalStateException(
          "Run ${run.id} is already in a terminal state (${run.state}). It can't be stopped.")
    }
    workflowService.stopWorkflow(run)
    val stoppedRun = run.copy(state = RunState.Failed)
    runRepository.save(stoppedRun)
  }

  private fun Run.hasPermission(permission: String) = apply {
    val runner =
        runnerApiService.getRunner(this.organizationId!!, this.workspaceId!!, this.runnerId!!)
    csmRbac.verify(runner.getRbac(), permission, getScenarioRolesDefinition())
  }

  internal fun checkInternalResultDataServiceConfiguration() {
    if (!csmPlatformProperties.useInternalResultServices) {
      throw NotImplementedException(notImplementedExceptionMessage)
    }
  }
}
