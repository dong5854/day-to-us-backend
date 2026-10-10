package com.dong.daytous.repository

import com.dong.daytous.domain.fixedexpense.FixedExpense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FixedExpenseRepository : JpaRepository<FixedExpense, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    override fun findById(id: UUID): java.util.Optional<FixedExpense>

    @Query("SELECT fe.id FROM FixedExpense fe")
    fun findAllIds(): List<UUID>

    fun findBySharedSpaceId(sharedSpaceId: UUID): List<FixedExpense>

    @Query("SELECT fe FROM FixedExpense fe JOIN FETCH fe.sharedSpace")
    fun findAllWithSharedSpace(): List<FixedExpense>
}
