package com.dong.daytous.service

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.SharedSpaceRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BudgetService(
    private val budgetEntryRepository: BudgetEntryRepository,
    private val sharedSpaceRepository: SharedSpaceRepository
) {
    @Transactional(readOnly = true)
    fun getAllBudgetEntriesForSpace(spaceId: Long): List<BudgetEntry> {
        return budgetEntryRepository.findBySharedSpaceId(spaceId)
    }

    @Transactional(readOnly = true)
    fun getBudgetEntryById(spaceId: Long, entryId: Long): BudgetEntry? {
        val entry = budgetEntryRepository.findByIdOrNull(entryId)
        return entry?.takeIf { it.sharedSpace?.id == spaceId }
    }

    @Transactional
    fun createBudgetEntry(spaceId: Long, request: BudgetEntryRequest): BudgetEntry {
        val sharedSpace = sharedSpaceRepository.findById(spaceId)
            .orElseThrow { EntityNotFoundException("SharedSpace with id $spaceId not found") }

        val newEntry = BudgetEntry(
            description = request.description,
            amount = request.amount,
            sharedSpace = sharedSpace
        )
        return budgetEntryRepository.save(newEntry)
    }

    @Transactional
    fun updateBudgetEntry(spaceId: Long, entryId: Long, request: BudgetEntryRequest): BudgetEntry? {
        return getBudgetEntryById(spaceId, entryId)?.let { existingEntry ->
            val updatedEntry = existingEntry.copy(
                description = request.description,
                amount = request.amount
            )
            budgetEntryRepository.save(updatedEntry)
        }
    }

    @Transactional
    fun deleteBudgetEntry(spaceId: Long, entryId: Long): Boolean {
        return getBudgetEntryById(spaceId, entryId)?.let {
            budgetEntryRepository.deleteById(entryId)
            true
        } ?: false
    }
}
