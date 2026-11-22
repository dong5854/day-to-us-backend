package com.dong.daytous.repository

import com.dong.daytous.domain.kis.KisConnection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KisConnectionRepository : JpaRepository<KisConnection, UUID> {
    fun findByOwnerEmail(ownerEmail: String): List<KisConnection>
    fun findByPlanSharedSpaceId(spaceId: UUID): List<KisConnection>
}
