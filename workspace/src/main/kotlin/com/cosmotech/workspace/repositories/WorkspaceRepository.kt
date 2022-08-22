package com.cosmotech.workspace.repositories

import com.cosmotech.workspace.domain.Workspace
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.stereotype.Repository

@Repository interface WorkspaceRepository : RedisDocumentRepository<Workspace, String>
