package com.dong.daytous.repository

import com.dong.daytous.domain.budget.BudgetEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BudgetEntryRepository : JpaRepository<BudgetEntry, Long> {
    fun findBySharedSpaceId(spaceId: Long): List<BudgetEntry>
}
