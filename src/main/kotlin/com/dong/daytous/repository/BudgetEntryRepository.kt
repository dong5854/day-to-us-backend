package com.dong.daytous.repository

import com.dong.daytous.domain.budget.BudgetEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BudgetEntryRepository : JpaRepository<BudgetEntry, UUID> {
    fun findBySharedSpaceId(spaceId: UUID): List<BudgetEntry>
}
