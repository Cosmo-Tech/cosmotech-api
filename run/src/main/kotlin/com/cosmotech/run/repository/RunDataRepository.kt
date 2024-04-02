package com.cosmotech.run.repository

import com.cosmotech.run.domain.RunData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository


@Repository
interface RunDataRepository: JpaRepository<RunData, String> {
}
