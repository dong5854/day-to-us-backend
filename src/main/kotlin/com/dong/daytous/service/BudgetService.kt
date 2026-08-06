package com.dong.daytous.service

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.ExpenseCategoryRepository
import com.dong.daytous.repository.PaymentMethodRepository
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
    private val expenseCategoryRepository: ExpenseCategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
) {
    @Transactional(readOnly = true)
    fun getAllBudgetEntriesForSpace(
        spaceId: UUID,
        year: Int?,
        month: Int?,
        email: String,
    ): List<BudgetEntry> {
        checkAccess(spaceId, email)

        if (year != null && month != null) {
            val yearMonth = java.time.YearMonth.of(year, month)
            val startDate = yearMonth.atDay(1)
            val endDate = yearMonth.atEndOfMonth()
            return budgetEntryRepository.findBySharedSpaceIdAndDateBetween(spaceId, startDate, endDate)
        }

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
        val sharedSpace = sharedSpaceRepository
            .findById(spaceId)
            .orElseThrow { EntityNotFoundException("SharedSpace with id $spaceId not found") }

        val category = request.categoryId?.let {
            expenseCategoryRepository.findByIdAndSharedSpaceId(it, spaceId)
                ?: throw EntityNotFoundException("ExpenseCategory with id $it not found in space $spaceId")
        }
        val paymentMethod = request.paymentMethodId?.let {
            paymentMethodRepository.findByIdAndSharedSpaceId(it, spaceId)
                ?: throw EntityNotFoundException("PaymentMethod with id $it not found in space $spaceId")
        }

        val newEntry = BudgetEntry(
            description = request.description,
            amount = request.amount,
            date = request.date,
            sharedSpace = sharedSpace,
            category = category,
            paymentMethod = paymentMethod,
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

        val category = request.categoryId?.let {
            expenseCategoryRepository.findByIdAndSharedSpaceId(it, spaceId)
                ?: throw EntityNotFoundException("ExpenseCategory with id $it not found in space $spaceId")
        }
        val paymentMethod = request.paymentMethodId?.let {
            paymentMethodRepository.findByIdAndSharedSpaceId(it, spaceId)
                ?: throw EntityNotFoundException("PaymentMethod with id $it not found in space $spaceId")
        }

        val updatedEntry = existingEntry.copy(
            description = request.description,
            amount = request.amount,
            date = request.date,
            category = category,
            paymentMethod = paymentMethod,
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
        val user = userRepository
            .findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }

        if (user.sharedSpace?.id != spaceId) {
            throw IllegalArgumentException("Access denied: User does not belong to this shared space")
        }
    }
}
