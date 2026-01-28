package com.dong.daytous.dto

import java.time.LocalDate
import java.util.UUID

data class BudgetEntryResponse(
    val id: UUID,
    val description: String,
    val amount: Double,
    val date: LocalDate,
    val fixedExpenseId: UUID?
)
