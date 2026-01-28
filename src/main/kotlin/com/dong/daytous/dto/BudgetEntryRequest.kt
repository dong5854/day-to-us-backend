package com.dong.daytous.dto

import java.util.UUID

data class BudgetEntryRequest(
    val description: String,
    val amount: Double,
    val fixedExpenseId: UUID? = null
)
