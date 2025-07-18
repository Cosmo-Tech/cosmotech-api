// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.RunDeleted
import com.cosmotech.api.events.RunStart
import com.cosmotech.api.events.RunStop
import com.cosmotech.api.events.RunnerDeleted
import com.cosmotech.api.events.UpdateRunnerStatus
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.getRunnerRolesDefinition
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.getCurrentAccountIdentifier
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
import com.cosmotech.run.domain.RunEditInfo
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
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.sql.SQLException
import java.time.Instant
import org.apache.commons.lang3.NotImplementedException
import org.postgresql.util.PGobject
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

internal const val WORKFLOW_TYPE_RUN = "container-run"

internal const val BOOLEAN_KEY_WEIGHT = 0
internal const val BOOLEAN_POSTGRESQL_TYPE = "BOOLEAN"
internal const val NUMERIC_KEY_WEIGHT = 1
internal const val NUMERIC_POSTGRESQL_TYPE = "NUMERIC"
internal const val TEXT_KEY_WEIGHT = 2
internal const val TEXT_POSTGRESQL_TYPE = "TEXT"
internal const val JSON_KEY_WEIGHT = 3
internal const val JSON_POSTGRESQL_TYPE = "JSONB"
internal const val CONFLICT_KEY_WEIGHT = JSON_KEY_WEIGHT
internal const val POSTGRESQL_DRIVER_CLASS_NAME = "org.postgresql.Driver"

internal val jsonTypeMapWeight =
    mapOf(
        Boolean::class.simpleName to BOOLEAN_KEY_WEIGHT,
        Int::class.simpleName to NUMERIC_KEY_WEIGHT,
        Double::class.simpleName to NUMERIC_KEY_WEIGHT,
        String::class.simpleName to TEXT_KEY_WEIGHT,
        ArrayList::class.simpleName to JSON_KEY_WEIGHT,
        LinkedHashMap::class.simpleName to JSON_KEY_WEIGHT)

internal val postgresTypeFromWeight =
    mapOf(
        BOOLEAN_KEY_WEIGHT to BOOLEAN_POSTGRESQL_TYPE,
        NUMERIC_KEY_WEIGHT to NUMERIC_POSTGRESQL_TYPE,
        TEXT_KEY_WEIGHT to TEXT_POSTGRESQL_TYPE,
        JSON_KEY_WEIGHT to JSON_POSTGRESQL_TYPE)

internal val postgresTypeToWeight =
    mapOf(
        BOOLEAN_POSTGRESQL_TYPE to BOOLEAN_KEY_WEIGHT,
        NUMERIC_POSTGRESQL_TYPE to NUMERIC_KEY_WEIGHT,
        TEXT_POSTGRESQL_TYPE to TEXT_KEY_WEIGHT,
        JSON_POSTGRESQL_TYPE to JSON_KEY_WEIGHT)

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

  @Value("\${csm.platform.internalResultServices.storage.admin.username}")
  private lateinit var adminStorageUsername: String

  @Value("\${csm.platform.internalResultServices.storage.admin.password}")
  private lateinit var adminStoragePassword: String

  @Value("\${csm.platform.internalResultServices.storage.writer.username}")
  private lateinit var writerStorageUsername: String

  @Value("\${csm.platform.internalResultServices.storage.writer.password}")
  private lateinit var writerStoragePassword: String

  @Value("\${csm.platform.internalResultServices.storage.reader.username}")
  private lateinit var readerStorageUsername: String

  @Value("\${csm.platform.internalResultServices.storage.reader.password}")
  private lateinit var readerStoragePassword: String

  @Value("\${csm.platform.internalResultServices.storage.host}") private lateinit var host: String
  @Value("\${csm.platform.internalResultServices.storage.port}") private lateinit var port: String

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

    val defaultPageSize = csmPlatformProperties.twincache.run.defaultPageSize
    val pageable =
        constructPageRequest(page, size, defaultPageSize) ?: PageRequest.of(0, defaultPageSize)

    return runRepository
        .findByRunnerId(organizationId, workspaceId, runnerId, pageable)
        .toList()
        .map { it.withStateInformation().withoutSensitiveData() }
  }

  override fun listAllRuns(
      organizationId: String,
      workspaceId: String,
      runnerId: String
  ): List<Run> {
    // This call verify the user read authorization in the Runner
    runnerApiService.getRunner(organizationId, workspaceId, runnerId)

    val defaultPageSize = csmPlatformProperties.twincache.run.defaultPageSize
    var pageRequest: Pageable = PageRequest.ofSize(defaultPageSize)

    val runs = mutableListOf<Run>()

    do {
      val pagedRuns =
          runRepository.findByRunnerId(organizationId, workspaceId, runnerId, pageRequest)
      runs.addAll(pagedRuns.toList().map { it.withStateInformation().withoutSensitiveData() })
      pageRequest = pagedRuns.nextPageable()
    } while (pagedRuns.hasNext())

    return runs
  }

  @Suppress("LongMethod", "ThrowsCount")
  fun sendDataToStorage(
      runId: String,
      tableName: String,
      data: List<Map<String, Any>>,
      isProbeData: Boolean = false
  ): RunData {
    val dataTableName = tableName.toDataTableName(isProbeData)

    if (!dataTableName.matches(Regex("\\w+"))) {
      throw SQLException("Table name \"$dataTableName\" is not a valid SQL identifier")
    }

    // Start by looking through the data for postgresql column type inference
    // Make use of a "weight" system for each data type to ensure most specific type when executed
    val dataKeyWeight = mutableMapOf<String, Int>()

    val treatedData = mutableListOf<Map<String, Any>>()
    data.forEach { dataLine ->
      val newDataLine = mutableMapOf<String, Any>()
      dataLine.keys.forEach { key -> newDataLine[key.lowercase()] = dataLine[key]!! }
      treatedData.add(newDataLine)
    }

    treatedData.forEach { dataLine ->
      dataLine.keys.forEach { key ->
        // Get weight for a given column
        if (!key.matches(Regex("\\w+"))) {
          throw SQLException("Column name \"$key\" is not a valid SQL identifier")
        }
        val keyWeight =
            jsonTypeMapWeight.getOrDefault(dataLine[key]!!::class.simpleName, CONFLICT_KEY_WEIGHT)
        if (!dataKeyWeight.containsKey(key)) dataKeyWeight[key] = keyWeight
        // If a conflict exists between 2 values in a same column use default value instead
        else if (dataKeyWeight[key] != keyWeight) dataKeyWeight[key] = CONFLICT_KEY_WEIGHT
      }
    }

    val dataKeyType = dataKeyWeight.mapValues { postgresTypeFromWeight[it.value]!! }.toMutableMap()
    val dataKeys = dataKeyType.keys

    val gson = Gson()

    val runtimeDS =
        DriverManagerDataSource(
            "jdbc:postgresql://$host:$port/$runId", writerStorageUsername, writerStoragePassword)

    runtimeDS.setDriverClassName(POSTGRESQL_DRIVER_CLASS_NAME)
    val runDBJdbcTemplate = JdbcTemplate(runtimeDS)
    val connection = runDBJdbcTemplate.dataSource!!.connection

    try {
      // Start revert-able code
      connection.autoCommit = false
      if (!runDBJdbcTemplate.existTable(dataTableName)) {
        // Table does not exist
        connection
            .prepareStatement(
                "CREATE TABLE $dataTableName " +
                    "( ${dataKeys.joinToString(separator = ", ") { "$it ${dataKeyType[it]!!}" }} ) ")
            .executeUpdate()

        logger.debug("Creating new table $dataTableName for run $runId")
      } else {
        // The table exist already

        // Start by getting the table schema (column name + type)
        val listColumnsPreparedStatement =
            connection.prepareStatement(
                "SELECT column_name, upper(data_type::text) as column_type " +
                    "FROM information_schema.columns WHERE table_name ilike ?")
        listColumnsPreparedStatement.setString(1, dataTableName)
        val postgresTableKeyResultSet = listColumnsPreparedStatement.executeQuery()

        val postgresTableKeysType = mutableMapOf<String, String>()

        // Each line contains "column_name" and "column_type"
        while (postgresTableKeyResultSet.next()) postgresTableKeysType[
            postgresTableKeyResultSet.getString("column_name")] =
            postgresTableKeyResultSet.getString("column_type")

        val postgresTableKeys: Set<String> = postgresTableKeysType.keys
        val postgresTableKeyWeight =
            postgresTableKeysType.mapValues { postgresTypeToWeight[it.value]!! }

        val missingKeys = dataKeys - postgresTableKeys
        val commonKeys = dataKeys - missingKeys

        // For each column both in data and existing table make a type check
        commonKeys.forEach { commonKey ->
          val columnKeyWeight = postgresTableKeyWeight[commonKey]!!
          // If the existing type in the database is not compatible with the type of the data throw
          // an error
          if (columnKeyWeight != CONFLICT_KEY_WEIGHT &&
              dataKeyWeight[commonKey]!! != columnKeyWeight)
              throw SQLException(
                  "Column $commonKey can not be converted to ${postgresTableKeysType[commonKey]!!}")
          else dataKeyType[commonKey] = postgresTableKeysType[commonKey]!!
        }
        missingKeys.forEach { missingKey ->
          // Alter the table for each missing key to add the column missing
          logger.debug("Adding COLUMN $missingKey on table $dataTableName for run $runId")
          connection
              .prepareStatement(
                  "ALTER TABLE $dataTableName ADD COLUMN $missingKey ${dataKeyType[missingKey]!!}")
              .executeUpdate()
        }
      }
      treatedData.forEach { dataLine ->
        // Insertion of data using prepared statements
        // for each key a parameter is created in the SQL query
        // that is then replaced by a string representation of the data that is then cast to the
        // correct type
        val insertPreparedStatement =
            connection.prepareStatement(
                "INSERT INTO $dataTableName ( ${dataLine.keys.joinToString(separator = ", ") {"$it"}} ) " +
                    "VALUES ( ${dataLine.keys.joinToString(separator = ", ") { "?::${dataKeyType[it]!!}" }} )")
        // insert all values as pure data into the statement ensuring no SQL can be executed
        // inside the query
        dataLine.keys.forEachIndexed { index, key ->
          if (dataKeyType[key] == JSON_POSTGRESQL_TYPE)
              insertPreparedStatement.setString(index + 1, gson.toJson(dataLine[key]))
          else insertPreparedStatement.setObject(index + 1, dataLine[key])
        }
        insertPreparedStatement.executeUpdate()
      }

      logger.debug("Inserted ${data.size} rows in table $dataTableName for run $runId")
      connection.commit()
    } catch (e: SQLException) {
      connection.rollback()
      throw e
    } finally {
      connection.close()
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

    require(sendRunDataRequest.data.isNotEmpty()) { "Data field cannot be empty" }

    return this.sendDataToStorage(runId, sendRunDataRequest.id, sendRunDataRequest.data)
  }

  override fun queryRunData(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String,
      runDataQuery: RunDataQuery
  ): QueryResult {

    checkInternalResultDataServiceConfiguration()
    getRun(organizationId, workspaceId, runnerId, runId)

    val runtimeDS =
        DriverManagerDataSource(
            "jdbc:postgresql://$host:$port/$runId", readerStorageUsername, readerStoragePassword)
    runtimeDS.setDriverClassName(POSTGRESQL_DRIVER_CLASS_NAME)

    val runDBJdbcTemplate = JdbcTemplate(runtimeDS)
    val connection = runDBJdbcTemplate.dataSource!!.connection

    val preparedStatement = connection.prepareStatement(runDataQuery.query)
    val queryResults = preparedStatement.executeQuery()

    val results = mutableListOf<Map<String, Any>>()
    val gson = Gson()
    val mapAdapter = gson.getAdapter(object : TypeToken<Map<String, Any>>() {})
    val arrayAdapter = gson.getAdapter(object : TypeToken<Array<Any>>() {})
    while (queryResults.next()) {
      val row = mutableMapOf<String, Any>()
      for (i in 1..queryResults.metaData.columnCount) {
        if (queryResults.getObject(i) == null) continue

        val resultValue = queryResults.getObject(i)
        if (resultValue is PGobject) {
          val parsedValue = JsonParser.parseString(resultValue.value)

          if (parsedValue.isJsonObject)
              row[queryResults.metaData.getColumnName(i)] = mapAdapter.fromJson(resultValue.value)
          else if (parsedValue.isJsonArray)
              row[queryResults.metaData.getColumnName(i)] = arrayAdapter.fromJson(resultValue.value)
          else if (parsedValue.asJsonPrimitive.isBoolean)
              row[queryResults.metaData.getColumnName(i)] = parsedValue.asBoolean
          else if (parsedValue.asJsonPrimitive.isNumber)
              row[queryResults.metaData.getColumnName(i)] = parsedValue.asNumber
          else row[queryResults.metaData.getColumnName(i)] = parsedValue.asString
        } else row[queryResults.metaData.getColumnName(i)] = resultValue
      }
      results.add(row)
    }

    return QueryResult(results)
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

  fun getRunStatus(run: Run): RunStatus {
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
    deleteRun(run)
  }

  private fun deleteRun(run: Run) {
    try {

      workflowService.stopWorkflow(run)

      if (csmPlatformProperties.internalResultServices?.enabled == true)
          adminRunStorageTemplate.dropDB(run.id!!)

      runRepository.delete(run)

      val runs = listRuns(run.organizationId!!, run.workspaceId!!, run.runnerId!!, null, null)
      var lastRun: String? = null
      var lastStart: Long = 0
      if (runs.isNotEmpty()) {
        runs.forEach {
          if (it.createInfo.timestamp >= lastStart) {
            lastStart = it.createInfo.timestamp
            lastRun = it.id!!
          }
        }
      } else {
        lastRun = null
      }
      val runDeleted =
          RunDeleted(this, run.organizationId, run.workspaceId, run.runnerId, run.id!!, lastRun)
      this.eventPublisher.publishEvent(runDeleted)
    } catch (exception: IllegalStateException) {
      logger.debug(
          "An error occurred while deleting Run {}: {}", run.id, exception.message, exception)
    }
  }

  override fun getRunLogs(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): String {
    val run = getRun(organizationId, workspaceId, runnerId, runId)
    val status = getRunStatus(run)
    if (status.endTime.isNullOrEmpty()) {
      try {
        return workflowService.getRunningLogs(run)
      } catch (e: RestClientResponseException) {
        if (e.statusCode != HttpStatus.NOT_FOUND) throw e
      }
    }
    return workflowService.getArchivedLogs(run)
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

    if (csmPlatformProperties.internalResultServices?.enabled == true) {
      val dbComment =
          "organizationId=${runner.organizationId}, workspaceId=${runner.workspaceId}, runnerId=${runner.id}"
      adminRunStorageTemplate.createDB(runId, dbComment)

      val runtimeDS =
          DriverManagerDataSource(
              "jdbc:postgresql://$host:$port/$runId", adminStorageUsername, adminStoragePassword)
      runtimeDS.setDriverClassName(POSTGRESQL_DRIVER_CLASS_NAME)
      val runDBJdbcTemplate = JdbcTemplate(runtimeDS)
      val connection = runDBJdbcTemplate.dataSource!!.connection
      try {
        connection.autoCommit = false
        connection
            .prepareStatement("GRANT CONNECT ON DATABASE \"$runId\" TO $readerStorageUsername")
            .executeUpdate()
        connection
            .prepareStatement("GRANT CONNECT ON DATABASE \"$runId\" TO $writerStorageUsername")
            .executeUpdate()

        connection
            .prepareStatement("GRANT CREATE ON SCHEMA public to $writerStorageUsername")
            .executeUpdate()

        connection
            .prepareStatement("GRANT USAGE ON SCHEMA public to $writerStorageUsername")
            .executeUpdate()

        connection
            .prepareStatement(
                "ALTER DEFAULT PRIVILEGES FOR USER  $writerStorageUsername IN SCHEMA public " +
                    "GRANT SELECT ON TABLES TO $readerStorageUsername;")
            .executeUpdate()
        connection.commit()
      } catch (e: SQLException) {
        connection.rollback()
        throw e
      } finally {
        connection.close()
      }
    }

    val startInfo =
        containerFactory.getStartInfo(
            runner.organizationId, runner.workspaceId, runner.id, WORKFLOW_TYPE_RUN, runId)
    val runRequest =
        workflowService.launchRun(
            runner.organizationId,
            runner.workspaceId,
            startInfo.startContainers,
            startInfo.runTemplate.executionTimeout,
            startInfo.solution.alwaysPull ?: false)
    val run = this.dbCreateRun(runId, runner, startInfo, runRequest)
    runStartRequest.response = run.id
  }

  private fun dbCreateRun(
      runId: String,
      runner: Runner,
      startInfo: StartInfo,
      runRequest: Run
  ): Run {
    val now = Instant.now().toEpochMilli()
    val run =
        runRequest.copy(
            id = runId,
            organizationId = runner.organizationId,
            workspaceId = runner.workspaceId,
            runnerId = runner.id,
            solutionId = startInfo.solution.id,
            runTemplateId = startInfo.runTemplate.id,
            generateName = startInfo.startContainers.generateName,
            computeSize = startInfo.runTemplate.computeSize,
            datasetList = runner.datasetList,
            createInfo =
                RunEditInfo(
                    timestamp = now, userId = getCurrentAccountIdentifier(csmPlatformProperties)),
            parametersValues =
                (runner.parametersValues.map {
                      RunTemplateParameterValue(
                          parameterId = it.parameterId, varType = it.varType, value = it.value)
                    })
                    .toList(),
            nodeLabel = startInfo.startContainers.nodeLabel,
            containers = startInfo.startContainers.containers,
        )
    logger.info(
        "[organizationId=${run.organizationId}]" +
            "[workspaceId=${run.workspaceId}]" +
            "[runnerId=${run.runnerId}]" +
            "[runId=${run.id}] has been launched by " +
            "[ownerId=${run.createInfo.userId}]")
    return runRepository.save(run)
  }

  @EventListener(UpdateRunnerStatus::class)
  fun updateRunnerStatus(updateRunnerStatus: UpdateRunnerStatus) {
    val organizationId = updateRunnerStatus.organizationId
    val workspaceId = updateRunnerStatus.workspaceId
    val runnerId = updateRunnerStatus.runnerId
    val runId = updateRunnerStatus.lastRunId
    if (runId.isNotEmpty()) {
      val run =
          runRepository
              .findBy(organizationId, workspaceId, runnerId, runId)
              .orElseThrow {
                throw IllegalArgumentException(
                    "Run #$runId not found in #$runnerId. In #$workspaceId, #$organizationId.")
              }
              .withStateInformation()
              .withoutSensitiveData()

      val status = getRunStatus(run).state
      updateRunnerStatus.response = status.toString()
      return
    }
    throw IllegalStateException("LastRunId for runner $runnerId cannot be null!")
  }

  @EventListener(RunStop::class)
  fun onRunStop(runStopRequest: RunStop) {
    val runner = runStopRequest.runnerData as Runner
    val run =
        getRun(runner.organizationId, runner.workspaceId, runner.id, runner.lastRunInfo.lastRunId!!)
    run.hasPermission(PERMISSION_WRITE)

    check(!(run.state!!.isTerminal())) {
      "Run ${run.id} is already in a terminal state (${run.state}). It can't be stopped."
    }

    workflowService.stopWorkflow(run)
    val stoppedRun = run.copy(state = RunState.Failed)
    runRepository.save(stoppedRun)
  }

  private fun Run.hasPermission(permission: String) = apply {
    val runner =
        runnerApiService.getRunner(this.organizationId!!, this.workspaceId!!, this.runnerId!!)
    csmRbac.verify(runner.getRbac(), permission, getRunnerRolesDefinition())
  }

  internal fun checkInternalResultDataServiceConfiguration() {
    if (csmPlatformProperties.internalResultServices?.enabled != true) {
      throw NotImplementedException(notImplementedExceptionMessage)
    }
  }

  @EventListener(RunnerDeleted::class)
  fun onRunnerDeleted(runnerDeleted: RunnerDeleted) {
    listAllRuns(runnerDeleted.organizationId, runnerDeleted.workspaceId, runnerDeleted.runnerId)
        .forEach { deleteRun(it) }
  }
}
