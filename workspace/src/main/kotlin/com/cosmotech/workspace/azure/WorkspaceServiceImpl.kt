// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.SqlParameter
import com.azure.cosmos.models.SqlQuerySpec
import com.azure.spring.autoconfigure.storage.resource.AzureStorageResourcePatternResolver
import com.azure.spring.autoconfigure.storage.resource.BlobStorageResource
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.batch.BlobBatchClient
import com.azure.storage.blob.models.DeleteSnapshotsOptionType
import com.cosmotech.api.azure.CsmAzureService
import com.cosmotech.api.azure.findAll
import com.cosmotech.api.azure.findByIdOrThrow
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.events.DeleteHistoricalDataOrganization
import com.cosmotech.api.events.DeleteHistoricalDataWorkspace
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.getPermissions
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.security.coroutine.SecurityCoroutineContext
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.azure.getRbac
import com.cosmotech.organization.azure.setRbac
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceFile
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecret
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Scope
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("TooManyFunctions")
internal class WorkspaceServiceImpl(
    private val resourceLoader: ResourceLoader,
    private val organizationService: OrganizationApiService,
    private val solutionService: SolutionApiService,
    private val azureStorageBlobServiceClient: BlobServiceClient,
    private val azureStorageBlobBatchClient: BlobBatchClient,
    private val csmRbac: CsmRbac,
    private val resourceScanner: ResourceScanner,
    private val secretManager: SecretManager,
) : CsmAzureService(), WorkspaceApiService {

  override fun findAllWorkspaces(organizationId: String): List<Workspace> {
    val organization = organizationService.findOrganizationById(organizationId)
    val isAdmin = csmRbac.isAdmin(organization.getRbac(), getCommonRolesDefinition())
    if (isAdmin || !this.csmPlatformProperties.rbac.enabled) {
      return cosmosTemplate.findAll("${organizationId}_workspaces")
    }
    val templateQuery =
        "SELECT * FROM c " +
            "WHERE ARRAY_CONTAINS(c.security.accessControlList, {id: @ACL_USER}, true) " +
            "OR c.security.default NOT LIKE 'none'"
    logger.debug("Template query: $templateQuery")

    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)
    logger.debug("Getting workspaces for user $currentUser")
    return cosmosCoreDatabase
        .getContainer("${organizationId}_workspaces")
        .queryItems(
            SqlQuerySpec(templateQuery, listOf(SqlParameter("@ACL_USER", currentUser))),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull { it.toDomain<Workspace>() }
        .toList()
  }

  internal fun findWorkspaceByIdNoSecurity(organizationId: String, workspaceId: String): Workspace =
      cosmosTemplate.findByIdOrThrow(
          "${organizationId}_workspaces",
          workspaceId,
          "Workspace $workspaceId not found in organization $organizationId")

  override fun findWorkspaceById(organizationId: String, workspaceId: String): Workspace {
    val workspace: Workspace = this.findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ)
    return workspace
  }

  override fun createWorkspace(organizationId: String, workspace: Workspace): Workspace {
    val organization = organizationService.findOrganizationById(organizationId)
    // Needs security on Organization to check RBAC
    csmRbac.verify(organization.getRbac(), PERMISSION_CREATE_CHILDREN)
    // Validate Solution ID
    workspace.solution?.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)

    return cosmosTemplate.insert(
        "${organizationId}_workspaces",
        workspace.copy(
            id = idGenerator.generate("workspace"),
            ownerId = getCurrentAuthenticatedUserName(),
            security = workspace.security ?: initSecurity(currentUser)))
        ?: throw IllegalArgumentException("No Workspace returned in response: $workspace")
  }

  override fun deleteAllWorkspaceFiles(organizationId: String, workspaceId: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_WRITE)
    logger.debug("Deleting all files for workspace #{} ({})", workspace.id, workspace.name)

    GlobalScope.launch(SecurityCoroutineContext()) {
      // TODO Consider using a smaller coroutine scope
      val workspaceFiles =
          getWorkspaceFileResources(organizationId, workspaceId).map { it.url }.map {
            it.toExternalForm()
          }
      if (workspaceFiles.isEmpty()) {
        logger.debug("No file to delete for workspace $workspaceId")
      } else {
        azureStorageBlobBatchClient.deleteBlobs(workspaceFiles, DeleteSnapshotsOptionType.INCLUDE)
            .forEach { response ->
              logger.debug(
                  "Deleting blob with URL {} completed with status code {}",
                  response.request.url,
                  response.statusCode)
            }
      }
    }
  }

  override fun updateWorkspace(
      organizationId: String,
      workspaceId: String,
      workspace: Workspace
  ): Workspace {
    val existingWorkspace = findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    csmRbac.verify(existingWorkspace.getRbac(), PERMISSION_WRITE)
    // Security cannot be changed by updateWorkspace
    var hasChanged =
        existingWorkspace
            .compareToAndMutateIfNeeded(
                workspace, excludedFields = arrayOf("ownerId", "security", "solution"))
            .isNotEmpty()

    if (workspace.solution?.solutionId != null) {
      // Validate solution ID
      workspace.solution?.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
      existingWorkspace.solution = workspace.solution
      hasChanged = true
    }

    if (workspace.security != null && existingWorkspace.security == null) {
      if (csmRbac.isAdmin(workspace.getRbac(), getCommonRolesDefinition())) {
        existingWorkspace.security = workspace.security
        hasChanged = true
      } else {
        logger.warn(
            "Security cannot by updated directly without admin permissions for ${workspace.id}")
      }
    }
    return if (hasChanged) {
      cosmosTemplate.upsertAndReturnEntity("${organizationId}_workspaces", existingWorkspace)
    } else {
      existingWorkspace
    }
  }

  override fun deleteWorkspace(organizationId: String, workspaceId: String): Workspace {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_DELETE)
    try {
      deleteAllWorkspaceFiles(organizationId, workspaceId)
      secretManager.deleteSecret(
          csmPlatformProperties.namespace, getWorkspaceSecretName(organizationId, workspace.key))
    } finally {
      cosmosTemplate.deleteEntity("${organizationId}_workspaces", workspace)
    }
    return workspace
  }

  override fun deleteWorkspaceFile(organizationId: String, workspaceId: String, fileName: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_WRITE)
    logger.debug(
        "Deleting file resource from workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    azureStorageBlobServiceClient
        .getBlobContainerClient(organizationId.sanitizeForAzureStorage())
        .getBlobClient("${workspaceId.sanitizeForAzureStorage()}/${fileName}")
        .delete()
  }

  override fun downloadWorkspaceFile(
      organizationId: String,
      workspaceId: String,
      fileName: String
  ): Resource {
    if (".." in fileName) {
      throw IllegalArgumentException("Invalid filename: '$fileName'. '..' is not allowed")
    }
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ)
    logger.debug(
        "Downloading file resource to workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    return resourceLoader.getResource(
        "azure-blob://$organizationId/$workspaceId/".sanitizeForAzureStorage() + fileName)
  }

  override fun uploadWorkspaceFile(
      organizationId: String,
      workspaceId: String,
      file: Resource,
      overwrite: Boolean,
      destination: String?
  ): WorkspaceFile {
    if (destination?.contains("..") == true) {
      throw IllegalArgumentException("Invalid destination: '$destination'. '..' is not allowed")
    }

    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_WRITE)
    logger.debug(
        "Uploading file resource to workspace #{} ({}): {} => {}",
        workspace.id,
        workspace.name,
        file.filename,
        destination)

    resourceScanner.scanMimeTypes(file, csmPlatformProperties.upload.authorizedMimeTypes.workspaces)
    val fileRelativeDestinationBuilder = StringBuilder()
    if (destination.isNullOrBlank()) {
      fileRelativeDestinationBuilder.append(file.filename)
    } else {
      // Using multiple consecutive '/' in the path results in Azure Storage creating
      // weird <empty> names in-between two subsequent '/'
      val destinationSanitized = destination.removePrefix("/").replace(Regex("(/)\\1+"), "/")
      fileRelativeDestinationBuilder.append(destinationSanitized)
      if (destinationSanitized.endsWith("/")) {
        fileRelativeDestinationBuilder.append(file.filename)
      }
    }

    azureStorageBlobServiceClient
        .getBlobContainerClient(organizationId.sanitizeForAzureStorage())
        .getBlobClient("${workspaceId.sanitizeForAzureStorage()}/$fileRelativeDestinationBuilder")
        .upload(file.inputStream, file.contentLength(), overwrite)
    return WorkspaceFile(fileName = fileRelativeDestinationBuilder.toString())
  }

  override fun findAllWorkspaceFiles(
      organizationId: String,
      workspaceId: String
  ): List<WorkspaceFile> {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ)
    logger.debug("List all files for workspace #{} ({})", workspace.id, workspace.name)
    return getWorkspaceFileResources(organizationId, workspaceId)
        .mapNotNull { it.filename?.removePrefix("${workspaceId.sanitizeForAzureStorage()}/") }
        .map { WorkspaceFile(fileName = it) }
  }

  override fun createSecret(
      organizationId: String,
      workspaceId: String,
      workspaceSecret: WorkspaceSecret
  ) {
    val workspaceKey = findWorkspaceById(organizationId, workspaceId).key
    secretManager.createOrReplaceSecret(
        csmPlatformProperties.namespace,
        getWorkspaceSecretName(organizationId, workspaceKey),
        mapOf(WORKSPACE_EVENTHUB_ACCESSKEY_SECRET to (workspaceSecret.dedicatedEventHubKey ?: "")))
  }

  @EventListener(DeleteHistoricalDataOrganization::class)
  fun deleteHistoricalDataWorkspace(data: DeleteHistoricalDataOrganization) {
    val organizationId = data.organizationId
    val workspaces: List<Workspace> = findAllWorkspaces(organizationId)
    for (workspace in workspaces) {
      this.eventPublisher.publishEvent(
          DeleteHistoricalDataWorkspace(this, organizationId, workspace.id!!, data.deleteUnknown))
    }
  }

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties("${organizationRegistered.organizationId}_workspaces", "/id"))
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    val organizationId = organizationUnregistered.organizationId
    try {
      azureStorageBlobServiceClient.deleteBlobContainer(organizationId)
    } finally {
      cosmosTemplate.deleteContainer("${organizationId}_workspaces")
    }
  }

  private fun getWorkspaceFileResources(
      organizationId: String,
      workspaceId: String
  ): List<BlobStorageResource> {
    findWorkspaceById(organizationId, workspaceId)
    return AzureStorageResourcePatternResolver(azureStorageBlobServiceClient)
        .getResources("azure-blob://$organizationId/$workspaceId/**/*".sanitizeForAzureStorage())
        .map { it as BlobStorageResource }
  }

  override fun getWorkspacePermissions(
      organizationId: String,
      workspaceId: String,
      role: String
  ): List<String> {
    return getPermissions(role, getCommonRolesDefinition())
  }

  override fun getWorkspaceSecurity(
      organizationId: String,
      workspaceId: String
  ): WorkspaceSecurity {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ_SECURITY)
    return workspace.security
        ?: throw CsmResourceNotFoundException("RBAC not defined for ${workspace.id}")
  }

  override fun setWorkspaceDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      workspaceRole: WorkspaceRole
  ): WorkspaceSecurity {
    val workspace = findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.setDefault(workspace.getRbac(), workspaceRole.role)
    workspace.setRbac(rbacSecurity)
    cosmosTemplate.upsertAndReturnEntity("${organizationId}_workspaces", workspace)
    return workspace.security as WorkspaceSecurity
  }

  override fun getWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ): WorkspaceAccessControl {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ_SECURITY)
    var rbacAccessControl = csmRbac.getAccessControl(workspace.getRbac(), identityId)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun addWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      workspaceAccessControl: WorkspaceAccessControl
  ): WorkspaceAccessControl {
    val workspace = findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_WRITE_SECURITY)
    var organization = organizationService.findOrganizationById(organizationId)
    val rbacSecurity =
        csmRbac.addUserRole(
            organization.getRbac(),
            workspace.getRbac(),
            workspaceAccessControl.id,
            workspaceAccessControl.role)
    workspace.setRbac(rbacSecurity)
    cosmosTemplate.upsertAndReturnEntity("${organizationId}_workspaces", workspace)
    var rbacAccessControl = csmRbac.getAccessControl(workspace.getRbac(), workspaceAccessControl.id)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String,
      workspaceRole: WorkspaceRole
  ): WorkspaceAccessControl {
    val workspace = findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        workspace.getRbac(), identityId, "User '$identityId' not found in workspace $workspaceId")
    val rbacSecurity = csmRbac.setUserRole(workspace.getRbac(), identityId, workspaceRole.role)
    workspace.setRbac(rbacSecurity)
    cosmosTemplate.upsertAndReturnEntity("${organizationId}_workspaces", workspace)
    val rbacAccessControl = csmRbac.getAccessControl(workspace.getRbac(), identityId)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun removeWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(workspace.getRbac(), identityId)
    workspace.setRbac(rbacSecurity)
    cosmosTemplate.upsertAndReturnEntity("${organizationId}_workspaces", workspace)
  }

  override fun getWorkspaceSecurityUsers(
      organizationId: String,
      workspaceId: String
  ): List<String> {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(workspace.getRbac())
  }

  private fun initSecurity(userId: String): WorkspaceSecurity {
    return WorkspaceSecurity(
        default = ROLE_NONE,
        accessControlList = mutableListOf(WorkspaceAccessControl(userId, ROLE_ADMIN)))
  }
}

fun Workspace.getRbac(): RbacSecurity {
  return RbacSecurity(
      this.id,
      this.security?.default ?: ROLE_NONE,
      this.security?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
          ?: mutableListOf())
}

fun Workspace.setRbac(rbacSecurity: RbacSecurity) {
  this.security =
      WorkspaceSecurity(
          rbacSecurity.default,
          rbacSecurity
              .accessControlList
              .map { WorkspaceAccessControl(it.id, it.role) }
              .toMutableList())
}
