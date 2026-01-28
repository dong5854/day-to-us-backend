package com.dong.daytous.dto

import com.dong.daytous.domain.budget.BudgetEntry

fun BudgetEntry.toResponse(): BudgetEntryResponse =
    BudgetEntryResponse(
        id = this.id ?: throw IllegalStateException("BudgetEntry ID cannot be null"),
        description = this.description,
        amount = this.amount,
        date = this.date,
        fixedExpenseId = this.fixedExpenseId
    )
