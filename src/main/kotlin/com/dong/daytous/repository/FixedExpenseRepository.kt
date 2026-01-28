package com.dong.daytous.repository

import com.dong.daytous.domain.fixedexpense.FixedExpense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FixedExpenseRepository : JpaRepository<FixedExpense, UUID> {
    fun findBySharedSpaceId(sharedSpaceId: UUID): List<FixedExpense>
}
