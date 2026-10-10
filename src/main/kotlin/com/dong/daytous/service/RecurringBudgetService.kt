package com.dong.daytous.service

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.domain.fixedexpense.FixedTransactionType
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.FixedExpenseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class RecurringBudgetService(
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val budgetEntryRepository: BudgetEntryRepository,
) {
    @Transactional
    fun postDueEntries(id: UUID, today: LocalDate) {
        // Lock the source so overlapping jobs commit entries and their cursor atomically.
        val source = fixedExpenseRepository.findById(id).orElse(null) ?: return
        if (source.postedThrough?.let { it >= today } == true) return
        val from = maxOf(source.autoPostFrom, source.postedThrough?.plusDays(1) ?: source.autoPostFrom)
        if (from > today) return
        var due = NotificationScheduler.calculateNextPaymentDate(source, from)
        while (due <= today) {
            val amount = source.amount.toDouble()
            budgetEntryRepository.save(BudgetEntry(
                description = source.description,
                amount = if (source.type == FixedTransactionType.INCOME) amount else -amount,
                date = due,
                sharedSpace = source.sharedSpace,
                category = source.category,
                paymentMethod = source.paymentMethod,
                fixedExpenseId = source.id,
            ))
            due = NotificationScheduler.calculateNextPaymentDate(source, due.plusDays(1))
        }
        source.postedThrough = today
        fixedExpenseRepository.save(source)
    }
}
