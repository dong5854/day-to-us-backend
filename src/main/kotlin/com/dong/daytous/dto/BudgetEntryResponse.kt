package com.dong.daytous.dto

import java.util.UUID

data class BudgetEntryResponse(
    val id: UUID,
    val description: String,
    val amount: Double,
)
