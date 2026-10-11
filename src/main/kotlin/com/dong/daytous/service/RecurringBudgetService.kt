package com.dong.daytous.service

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.domain.fixedexpense.FixedExpense
import com.dong.daytous.domain.fixedexpense.FixedTransactionType
import com.dong.daytous.domain.fixedexpense.RecurringOccurrence
import com.dong.daytous.dto.FixedExpenseRequest
import com.dong.daytous.dto.RecurringPreview
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.FixedExpenseRepository
import com.dong.daytous.repository.RecurringOccurrenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

@Service
class RecurringBudgetService(
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val budgetEntryRepository: BudgetEntryRepository,
    private val occurrences: RecurringOccurrenceRepository,
) {
    @Transactional
    fun lockAndTrack(id: UUID): FixedExpense? {
        val source = fixedExpenseRepository.findById(id).orElse(null) ?: return null
        if (!source.occurrencesTracked) {
            val existing = budgetEntryRepository.findByFixedExpenseId(id).groupBy { it.date }
            val dates = dueDates(source, source.autoPostFrom, source.postedThrough ?: source.autoPostFrom.minusDays(1)) + existing.keys
            dates.forEach { due ->
                occurrences.save(RecurringOccurrence(id, due, existing[due]?.firstOrNull()?.id, true))
            }
            source.occurrencesTracked = true
            fixedExpenseRepository.save(source)
        }
        return source
    }

    @Transactional
    fun protectEntry(entryId: UUID, deleted: Boolean = false) {
        occurrences.findByBudgetEntryId(entryId)?.let {
            it.protectedEntry = true
            if (deleted) it.budgetEntryId = null
            occurrences.save(it)
        }
    }

    @Transactional
    fun postDueEntries(id: UUID, today: LocalDate) {
        // The source lock serializes posting, edits, deletions and reconciliation.
        val source = lockAndTrack(id) ?: return
        if (source.postedThrough?.let { it >= today } == true) return
        val from = maxOf(source.autoPostFrom, source.postedThrough?.plusDays(1) ?: source.autoPostFrom)
        if (from > today) return
        val known = occurrences.findByFixedExpenseId(id).map { it.scheduledDate }.toSet()
        dueDates(source, from, today).filterNot { it in known }.forEach { createEntry(source, it) }
        source.postedThrough = today
        fixedExpenseRepository.save(source)
    }

    private fun dueDates(source: FixedExpense, from: LocalDate, through: LocalDate): Set<LocalDate> {
        val dates = linkedSetOf<LocalDate>()
        var due = NotificationScheduler.calculateNextPaymentDate(source, from)
        while (due <= through) {
            dates.add(due)
            due = NotificationScheduler.calculateNextPaymentDate(source, due.plusDays(1))
        }
        return dates
    }

    private fun createEntry(source: FixedExpense, due: LocalDate) {
        val amount = source.amount.toDouble()
        val entry = budgetEntryRepository.save(BudgetEntry(
            description = source.description,
            amount = if (source.type == FixedTransactionType.INCOME) amount else -amount,
            date = due,
            sharedSpace = source.sharedSpace,
            category = source.category,
            paymentMethod = source.paymentMethod,
            fixedExpenseId = source.id,
        ))
        occurrences.save(RecurringOccurrence(source.id!!, due, entry.id))
    }

    private data class Plan(
        val add: Set<LocalDate>,
        val remove: List<RecurringOccurrence>,
        val preview: RecurringPreview,
    )

    private fun plan(source: FixedExpense, request: FixedExpenseRequest, today: LocalDate): Plan {
        val target = FixedExpense(request.description, request.amount, request.frequency, request.startDate,
            sharedSpace = source.sharedSpace, autoPostFrom = source.autoPostFrom)
        val desired = dueDates(target, source.autoPostFrom, today)
        val history = occurrences.findByFixedExpenseId(source.id!!).sortedBy { it.scheduledDate }
        val known = history.map { it.scheduledDate }.toSet()
        val add = desired - known
        val remove = history.filter { !it.protectedEntry && it.budgetEntryId != null && it.scheduledDate !in desired }
        val snapshot = listOf(today, source.id, source.startDate, source.frequency, source.autoPostFrom,
            source.postedThrough, source.amount, source.description, source.type, source.category?.id,
            source.paymentMethod?.id, request.copy(reconciliationToken = null),
            history.map { listOf(it.id, it.scheduledDate, it.budgetEntryId, it.protectedEntry) }).toString()
        val token = MessageDigest.getInstance("SHA-256").digest(snapshot.toByteArray()).joinToString("") { "%02x".format(it) }
        return Plan(add, remove, RecurringPreview(add.size, remove.size, history.count { it.protectedEntry }, token))
    }

    // Callers hold the source lock before previewing or applying a plan.
    fun preview(source: FixedExpense, request: FixedExpenseRequest, today: LocalDate): RecurringPreview =
        plan(source, request, today).preview

    fun reconcile(source: FixedExpense, updated: FixedExpense, request: FixedExpenseRequest, today: LocalDate) {
        val plan = plan(source, request, today)
        require(request.reconciliationToken == plan.preview.token) {
            "내역이 변경되었습니다. 변경 내용을 다시 확인한 후 저장해 주세요."
        }
        plan.remove.forEach { occurrence ->
            budgetEntryRepository.deleteById(occurrence.budgetEntryId!!)
            occurrences.delete(occurrence)
        }
        plan.add.forEach { createEntry(updated, it) }
        updated.postedThrough = today
    }
}
