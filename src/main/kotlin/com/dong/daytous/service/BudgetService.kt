package com.dong.daytous.service

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BudgetService(
    private val budgetEntryRepository: BudgetEntryRepository,
    private val sharedSpaceRepository: SharedSpaceRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getAllBudgetEntriesForSpace(
        spaceId: UUID,
        email: String,
    ): List<BudgetEntry> {
        checkAccess(spaceId, email)
        return budgetEntryRepository.findBySharedSpaceId(spaceId)
    }

    @Transactional(readOnly = true)
    fun getBudgetEntryById(
        spaceId: UUID,
        entryId: UUID,
        email: String,
    ): BudgetEntry {
        checkAccess(spaceId, email)
        return budgetEntryRepository
            .findByIdOrNull(entryId)
            ?.takeIf { it.sharedSpace?.id == spaceId }
            ?: throw EntityNotFoundException("BudgetEntry not found. spaceId=$spaceId, entryId=$entryId")
    }

    @Transactional
    fun createBudgetEntry(
        spaceId: UUID,
        request: BudgetEntryRequest,
        email: String,
    ): BudgetEntry {
        checkAccess(spaceId, email)
        val sharedSpace =
            sharedSpaceRepository
                .findById(spaceId)
                .orElseThrow { EntityNotFoundException("SharedSpace with id $spaceId not found") }

        val newEntry =
            BudgetEntry(
                description = request.description,
                amount = request.amount,
                sharedSpace = sharedSpace,
                fixedExpenseId = request.fixedExpenseId,
            )
        return budgetEntryRepository.save(newEntry)
    }

    @Transactional
    fun updateBudgetEntry(
        spaceId: UUID,
        entryId: UUID,
        request: BudgetEntryRequest,
        email: String,
    ): BudgetEntry {
        val existingEntry = getBudgetEntryById(spaceId, entryId, email)
        val updatedEntry =
            existingEntry.copy(
                description = request.description,
                amount = request.amount,
                fixedExpenseId = request.fixedExpenseId,
            )
        return budgetEntryRepository.save(updatedEntry)
    }

    @Transactional
    fun deleteBudgetEntry(
        spaceId: UUID,
        entryId: UUID,
        email: String,
    ) {
        val entry = getBudgetEntryById(spaceId, entryId, email)
        budgetEntryRepository.delete(entry)
    }

    private fun checkAccess(
        spaceId: UUID,
        email: String,
    ) {
        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { EntityNotFoundException("User not found") }

        if (user.sharedSpace?.id != spaceId) {
            throw IllegalArgumentException("Access denied: User does not belong to this shared space")
        }
    }
}
