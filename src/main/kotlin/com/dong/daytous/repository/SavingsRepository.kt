package com.dong.daytous.repository

import com.dong.daytous.domain.savings.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import java.util.UUID

interface SavingsPlanRepository : JpaRepository<SavingsPlan, UUID> {
    fun findBySharedSpaceId(spaceId: UUID): List<SavingsPlan>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SavingsPlan p where p.id = :id")
    fun lockById(id: UUID): SavingsPlan?
    @Query("select p.id from SavingsPlan p where p.archived = false and p.monthlyAmount is not null")
    fun scheduledIds(): List<UUID>
}
interface SavingsRecordRepository : JpaRepository<SavingsRecord, UUID> {
    fun findByPlanId(planId: UUID): List<SavingsRecord>
    @Query("select r from SavingsRecord r join fetch r.plan p where p.sharedSpace.id = :spaceId")
    fun findForSpace(spaceId: UUID): List<SavingsRecord>
}
