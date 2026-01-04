package com.dong.daytous.service

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.SharedSpaceRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BudgetService(
    private val budgetEntryRepository: BudgetEntryRepository,
    private val sharedSpaceRepository: SharedSpaceRepository,
) {
    @Transactional(readOnly = true)
    fun getAllBudgetEntriesForSpace(spaceId: UUID): List<BudgetEntry> = budgetEntryRepository.findBySharedSpaceId(spaceId)

    @Transactional(readOnly = true)
    fun getBudgetEntryById(
        spaceId: UUID,
        entryId: UUID,
    ): BudgetEntry =
        budgetEntryRepository
            .findByIdOrNull(entryId)
            ?.takeIf { it.sharedSpace?.id == spaceId }
            ?: throw EntityNotFoundException("BudgetEntry not found. spaceId=$spaceId, entryId=$entryId")

    @Transactional
    fun createBudgetEntry(
        spaceId: UUID,
        request: BudgetEntryRequest,
    ): BudgetEntry {
        val sharedSpace =
            sharedSpaceRepository
                .findById(spaceId)
                .orElseThrow { EntityNotFoundException("SharedSpace with id $spaceId not found") }

        val newEntry =
            BudgetEntry(
                description = request.description,
                amount = request.amount,
                sharedSpace = sharedSpace,
            )
        return budgetEntryRepository.save(newEntry)
    }

    @Transactional
    fun updateBudgetEntry(
        spaceId: UUID,
        entryId: UUID,
        request: BudgetEntryRequest,
    ): BudgetEntry {
        val existingEntry = getBudgetEntryById(spaceId, entryId)
        val updatedEntry =
            existingEntry.copy(
                description = request.description,
                amount = request.amount,
            )
        return budgetEntryRepository.save(updatedEntry)
    }

    @Transactional
    fun deleteBudgetEntry(
        spaceId: UUID,
        entryId: UUID,
    ) {
        val entry = getBudgetEntryById(spaceId, entryId)
        budgetEntryRepository.delete(entry)
    }
}
