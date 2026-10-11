package com.dong.daytous.repository

import com.dong.daytous.domain.fixedexpense.RecurringOccurrence
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RecurringOccurrenceRepository : JpaRepository<RecurringOccurrence, UUID> {
    fun findByFixedExpenseId(fixedExpenseId: UUID): List<RecurringOccurrence>
    fun findByBudgetEntryId(budgetEntryId: UUID): RecurringOccurrence?
}
