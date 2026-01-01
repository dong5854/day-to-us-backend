package com.dong.daytous.service

import com.dong.daytous.domain.BudgetEntry
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.repository.BudgetEntryRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BudgetService(
    private val budgetEntryRepository: BudgetEntryRepository,
) {
    fun getAllBudgetEntries(): List<BudgetEntry> = budgetEntryRepository.findAll()

    fun getBudgetEntryById(id: Long): BudgetEntry? = budgetEntryRepository.findByIdOrNull(id)

    @Transactional
    fun createBudgetEntry(request: BudgetEntryRequest): BudgetEntry {
        val newEntry =
            BudgetEntry(
                description = request.description,
                amount = request.amount,
            )
        return budgetEntryRepository.save(newEntry)
    }

    @Transactional
    fun updateBudgetEntry(
        id: Long,
        request: BudgetEntryRequest,
    ): BudgetEntry? =
        budgetEntryRepository.findByIdOrNull(id)?.let { existingEntry ->
            val updatedEntry =
                existingEntry.copy(
                    description = request.description,
                    amount = request.amount,
                )
            budgetEntryRepository.save(updatedEntry)
        }

    @Transactional
    fun deleteBudgetEntry(id: Long) {
        budgetEntryRepository.deleteById(id)
    }
}
