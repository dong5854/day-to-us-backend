package com.dong.daytous.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.util.UUID

data class BudgetEntryRequest(
    val description: String,
    val amount: Double,
    
    @field:NotNull
    val date: LocalDate,

    val fixedExpenseId: UUID? = null
)
