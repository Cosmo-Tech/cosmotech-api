// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.service

import com.amazonaws.SdkClientException
import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.events.WorkspaceDeleted
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.getPermissions
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.security.coroutine.SecurityCoroutineContext
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.service.toGenericSecurity
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceCreateRequest
import com.cosmotech.workspace.domain.WorkspaceEditInfo
import com.cosmotech.workspace.domain.WorkspaceFile
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceUpdateRequest
import com.cosmotech.workspace.repository.WorkspaceRepository
import io.awspring.cloud.s3.S3Exception
import io.awspring.cloud.s3.S3Template
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.context.event.EventListener
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request

private const val WORKSPACE_FILES_BASE_FOLDER = "workspace-files"

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Suppress("TooManyFunctions")
internal class WorkspaceServiceImpl(
    private val organizationService: OrganizationApiServiceInterface,
    private val solutionService: SolutionApiService,
    private val s3Client: S3Client,
    private val s3Template: S3Template,
    private val csmRbac: CsmRbac,
    private val resourceScanner: ResourceScanner,
    private val workspaceRepository: WorkspaceRepository
) : CsmPhoenixService(), WorkspaceApiServiceInterface {

  override fun listWorkspaces(organizationId: String, page: Int?, size: Int?): List<Workspace> {
    val organization = organizationService.getVerifiedOrganization(organizationId)
    val isAdmin =
        csmRbac.isAdmin(
            organization.security.toGenericSecurity(organizationId), getCommonRolesDefinition())
    val defaultPageSize = csmPlatformProperties.twincache.workspace.defaultPageSize
    var result: List<Workspace>
    var pageable = constructPageRequest(page, size, defaultPageSize)

    if (pageable == null) {
      result =
          findAllPaginated(defaultPageSize) {
            if (isAdmin || !this.csmPlatformProperties.rbac.enabled) {
              workspaceRepository.findByOrganizationId(organizationId, it).toList()
            } else {
              val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
              workspaceRepository
                  .findByOrganizationIdAndSecurity(organizationId, currentUser, it)
                  .toList()
            }
          }
    } else {
      if (isAdmin || !this.csmPlatformProperties.rbac.enabled) {
        result = workspaceRepository.findByOrganizationId(organizationId, pageable).toList()
      } else {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        result =
            workspaceRepository
                .findByOrganizationIdAndSecurity(organizationId, currentUser, pageable)
                .toList()
      }
    }
    result.forEach { it.security = updateSecurityVisibility(it).security }
    return result
  }

  override fun getWorkspace(organizationId: String, workspaceId: String): Workspace {
    return updateSecurityVisibility(getVerifiedWorkspace(organizationId, workspaceId))
  }

  override fun createWorkspace(
      organizationId: String,
      workspaceCreateRequest: WorkspaceCreateRequest
  ): Workspace {
    organizationService.getVerifiedOrganization(
        organizationId, listOf(PERMISSION_READ, PERMISSION_CREATE_CHILDREN))

    // Validate Solution ID
    workspaceCreateRequest.solution.solutionId.let {
      solutionService.getSolution(organizationId, it)
    }

    val workspaceId = idGenerator.generate("workspace")
    val now = Instant.now().toEpochMilli()
    val security =
        csmRbac
            .initSecurity(workspaceCreateRequest.security.toGenericSecurity(workspaceId))
            .toResourceSecurity()
    val createdWorkspace =
        Workspace(
            id = workspaceId,
            organizationId = organizationId,
            createInfo =
                WorkspaceEditInfo(
                    timestamp = now, userId = getCurrentAccountIdentifier(csmPlatformProperties)),
            updateInfo =
                WorkspaceEditInfo(
                    timestamp = now, userId = getCurrentAccountIdentifier(csmPlatformProperties)),
            key = workspaceCreateRequest.key,
            name = workspaceCreateRequest.name,
            solution = workspaceCreateRequest.solution,
            security = security,
            version = workspaceCreateRequest.version,
            tags = workspaceCreateRequest.tags,
            description = workspaceCreateRequest.description,
            webApp = workspaceCreateRequest.webApp,
            datasetCopy = workspaceCreateRequest.datasetCopy,
        )

    return workspaceRepository.save(createdWorkspace)
  }

  override fun deleteWorkspaceFiles(organizationId: String, workspaceId: String) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    CoroutineScope(SecurityCoroutineContext()).launch {
      deleteAllS3WorkspaceObjects(organizationId, workspace)
    }
  }

  override fun updateWorkspace(
      organizationId: String,
      workspaceId: String,
      workspaceUpdateRequest: WorkspaceUpdateRequest
  ): Workspace {
    val existingWorkspace = this.getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)

    val updatedWorkspace =
        Workspace(
            key = workspaceUpdateRequest.key ?: existingWorkspace.key,
            name = workspaceUpdateRequest.name ?: existingWorkspace.name,
            solution = workspaceUpdateRequest.solution ?: existingWorkspace.solution,
            id = existingWorkspace.id,
            organizationId = organizationId,
            description = workspaceUpdateRequest.description ?: existingWorkspace.description,
            tags = workspaceUpdateRequest.tags ?: existingWorkspace.tags,
            createInfo = existingWorkspace.createInfo,
            updateInfo = existingWorkspace.updateInfo,
            webApp = workspaceUpdateRequest.webApp ?: existingWorkspace.webApp,
            datasetCopy = workspaceUpdateRequest.datasetCopy ?: existingWorkspace.datasetCopy,
            security = existingWorkspace.security)

    // Validate that solutionId refers to an existing one
    if (updatedWorkspace.solution.solutionId != existingWorkspace.solution.solutionId) {
      updatedWorkspace.solution.solutionId.let { solutionService.getSolution(organizationId, it) }
    }

    val hasChanged =
        existingWorkspace
            .compareToAndMutateIfNeeded(
                updatedWorkspace, excludedFields = arrayOf("ownerId", "security"))
            .isNotEmpty()

    return if (hasChanged) {
      save(existingWorkspace)
    } else {
      existingWorkspace
    }
  }

  override fun deleteWorkspace(organizationId: String, workspaceId: String) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_DELETE)

    try {
      CoroutineScope(SecurityCoroutineContext()).launch {
        deleteAllS3WorkspaceObjects(organizationId, workspace)
      }
    } finally {
      workspaceRepository.delete(workspace)
      this.eventPublisher.publishEvent(WorkspaceDeleted(this, organizationId, workspaceId))
    }
  }

  override fun deleteWorkspaceFile(organizationId: String, workspaceId: String, fileName: String) {
    require(".." !in fileName) { "Invalid filename: '$fileName'. '..' is not allowed" }
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    deleteS3WorkspaceObject(organizationId, workspace, fileName)
  }

  override fun getWorkspaceFile(
      organizationId: String,
      workspaceId: String,
      fileName: String
  ): Resource {
    require(".." !in fileName) { "Invalid filename: '$fileName'. '..' is not allowed" }
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ)
    logger.debug(
        "Downloading file resource from workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    return InputStreamResource(
        s3Template
            .download(
                csmPlatformProperties.s3.bucketName,
                "$organizationId/$workspaceId/$WORKSPACE_FILES_BASE_FOLDER/$fileName")
            .inputStream)
  }

  override fun createWorkspaceFile(
      organizationId: String,
      workspaceId: String,
      file: MultipartFile,
      overwrite: Boolean,
      destination: String?
  ): WorkspaceFile {
    if (destination?.contains("..") == true) {
      throw IllegalArgumentException("Invalid destination: '$destination'. '..' is not allowed")
    }
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    if (file.originalFilename?.contains("..") == true ||
        file.originalFilename?.contains("/") == true) {
      throw IllegalArgumentException(
          "Invalid filename: '${file.originalFilename}'. '..' and '/' are not allowed")
    }

    logger.debug(
        "Uploading file resource to workspace #{} ({}): {} => {}",
        workspace.id,
        workspace.name,
        file.originalFilename,
        destination)

    resourceScanner.scanMimeTypes(
        file.originalFilename!!,
        file.inputStream,
        csmPlatformProperties.upload.authorizedMimeTypes.workspaces)
    val fileRelativeDestinationBuilder = StringBuilder()
    if (destination.isNullOrBlank()) {
      fileRelativeDestinationBuilder.append(file.originalFilename)
    } else {
      // Using multiple consecutive '/' in the path is not supported in the storage upload
      val destinationSanitized = destination.removePrefix("/").replace(Regex("(/)\\1+"), "/")
      fileRelativeDestinationBuilder.append(destinationSanitized)
      if (destinationSanitized.endsWith("/")) {
        fileRelativeDestinationBuilder.append(file.originalFilename)
      }
    }
    val objectKey =
        "$organizationId/$workspaceId/$WORKSPACE_FILES_BASE_FOLDER/$fileRelativeDestinationBuilder"

    if (!overwrite && s3Template.objectExists(csmPlatformProperties.s3.bucketName, objectKey)) {
      throw IllegalArgumentException(
          "File '$fileRelativeDestinationBuilder' already exists, not overwriting it")
    }

    s3Template.upload(csmPlatformProperties.s3.bucketName, objectKey, file.inputStream)
    return WorkspaceFile(fileName = fileRelativeDestinationBuilder.toString())
  }

  override fun listWorkspaceFiles(
      organizationId: String,
      workspaceId: String
  ): List<WorkspaceFile> {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId)

    logger.debug("List all files for workspace #{} ({})", workspace.id, workspace.name)

    return getWorkspaceFiles(organizationId, workspaceId)
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    val organizationId = organizationUnregistered.organizationId
    val pageable: Pageable =
        Pageable.ofSize(csmPlatformProperties.twincache.workspace.defaultPageSize)
    val workspaces = workspaceRepository.findByOrganizationId(organizationId, pageable).toList()
    workspaces.forEach {
      deleteAllS3WorkspaceObjects(organizationId, it)
      workspaceRepository.delete(it)
      if (csmPlatformProperties.internalResultServices?.enabled == true) {
        this.eventPublisher.publishEvent(WorkspaceDeleted(this, organizationId, it.id))
      }
    }
  }

  private fun getWorkspaceFiles(organizationId: String, workspaceId: String): List<WorkspaceFile> {
    val prefix = "${organizationId}/${workspaceId}/$WORKSPACE_FILES_BASE_FOLDER/"
    val listObjectsRequest =
        ListObjectsV2Request.builder()
            .bucket(csmPlatformProperties.s3.bucketName)
            .prefix(prefix)
            .build()

    return s3Client
        .listObjectsV2Paginator(listObjectsRequest)
        .stream()
        .flatMap {
          it.contents().stream().map { WorkspaceFile(fileName = it.key().removePrefix(prefix)) }
        }
        .toList()
  }

  private fun deleteS3WorkspaceObject(
      organizationId: String,
      workspace: Workspace,
      fileName: String
  ) {
    logger.debug(
        "Deleting file resource from workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    try {
      s3Template.deleteObject(
          csmPlatformProperties.s3.bucketName,
          "$organizationId/${workspace.id}/$WORKSPACE_FILES_BASE_FOLDER/$fileName")
    } catch (e: AwsServiceException) {
      logger.error("Something wrong happened during $fileName deletion", e)
    } catch (e: SdkClientException) {
      logger.error("Something wrong happened during $fileName deletion", e)
    } catch (e: S3Exception) {
      logger.error("Something wrong happened during $fileName deletion", e)
    }
  }

  fun deleteAllS3WorkspaceObjects(organizationId: String, workspace: Workspace) {
    logger.debug("Deleting all files for workspace #{} ({})", workspace.id, workspace.name)
    val workspaceFiles = getWorkspaceFiles(organizationId, workspace.id)
    if (workspaceFiles.isEmpty()) {
      logger.debug("No file to delete for workspace #{} ({})", workspace.id, workspace.name)
    } else {
      workspaceFiles.forEach { file ->
        deleteS3WorkspaceObject(organizationId, workspace, file.fileName)
      }
    }
  }

  override fun listWorkspaceRolePermissions(
      organizationId: String,
      workspaceId: String,
      role: String
  ): List<String> {
    getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    return getPermissions(role, getCommonRolesDefinition())
  }

  override fun getWorkspaceSecurity(
      organizationId: String,
      workspaceId: String
  ): WorkspaceSecurity {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    return workspace.security
  }

  override fun updateWorkspaceDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      workspaceRole: WorkspaceRole
  ): WorkspaceSecurity {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.setDefault(workspace.security.toGenericSecurity(workspaceId), workspaceRole.role)
    workspace.security = rbacSecurity.toResourceSecurity()
    save(workspace)
    return workspace.security
  }

  override fun getWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ): WorkspaceAccessControl {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    val rbacAccessControl =
        csmRbac.getAccessControl(workspace.security.toGenericSecurity(workspaceId), identityId)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun getVerifiedWorkspace(
      organizationId: String,
      workspaceId: String,
      requiredPermission: String
  ): Workspace {
    organizationService.getVerifiedOrganization(organizationId)
    val workspace =
        workspaceRepository.findBy(organizationId, workspaceId).orElseThrow {
          CsmResourceNotFoundException(
              "Workspace $workspaceId not found in organization $organizationId")
        }
    csmRbac.verify(workspace.security.toGenericSecurity(workspaceId), requiredPermission)
    return workspace
  }

  override fun createWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      workspaceAccessControl: WorkspaceAccessControl
  ): WorkspaceAccessControl {
    val organization = organizationService.getVerifiedOrganization(organizationId)
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    val users = csmRbac.getUsers(workspace.security.toGenericSecurity(workspaceId))
    require(!users.contains(workspaceAccessControl.id)) {
      "User is already in this Workspace security"
    }
    val rbacSecurity =
        csmRbac.addUserRole(
            organization.security.toGenericSecurity(organizationId),
            workspace.security.toGenericSecurity(workspaceId),
            workspaceAccessControl.id,
            workspaceAccessControl.role)
    workspace.security = rbacSecurity.toResourceSecurity()
    save(workspace)
    val rbacAccessControl =
        csmRbac.getAccessControl(
            workspace.security.toGenericSecurity(workspaceId), workspaceAccessControl.id)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String,
      workspaceRole: WorkspaceRole
  ): WorkspaceAccessControl {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        workspace.security.toGenericSecurity(workspaceId),
        identityId,
        "User '$identityId' not found in workspace $workspaceId")
    val rbacSecurity =
        csmRbac.setUserRole(
            workspace.security.toGenericSecurity(workspaceId), identityId, workspaceRole.role)
    workspace.security = rbacSecurity.toResourceSecurity()
    save(workspace)
    val rbacAccessControl =
        csmRbac.getAccessControl(workspace.security.toGenericSecurity(workspaceId), identityId)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun deleteWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.removeUser(workspace.security.toGenericSecurity(workspaceId), identityId)
    workspace.security = rbacSecurity.toResourceSecurity()
    save(workspace)
  }

  override fun listWorkspaceSecurityUsers(
      organizationId: String,
      workspaceId: String
  ): List<String> {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(workspace.security.toGenericSecurity(workspaceId))
  }

  fun updateSecurityVisibility(workspace: Workspace): Workspace {
    if (csmRbac
        .check(workspace.security.toGenericSecurity(workspace.id), PERMISSION_READ_SECURITY)
        .not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = workspace.security.accessControlList.firstOrNull { it.id == username }

      val accessControlList =
          if (retrievedAC != null) {
            mutableListOf(retrievedAC)
          } else {
            mutableListOf()
          }

      return workspace.copy(
          security =
              WorkspaceSecurity(
                  default = workspace.security.default, accessControlList = accessControlList))
    }
    return workspace
  }

  fun save(workspace: Workspace): Workspace {
    workspace.updateInfo =
        WorkspaceEditInfo(
            timestamp = Instant.now().toEpochMilli(),
            userId = getCurrentAccountIdentifier(csmPlatformProperties))
    return workspaceRepository.save(workspace)
  }
}

fun WorkspaceSecurity?.toGenericSecurity(workspaceId: String) =
    RbacSecurity(
        workspaceId,
        this?.default ?: ROLE_NONE,
        this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf())

fun RbacSecurity.toResourceSecurity() =
    WorkspaceSecurity(
        this.default,
        this.accessControlList.map { WorkspaceAccessControl(it.id, it.role) }.toMutableList())
