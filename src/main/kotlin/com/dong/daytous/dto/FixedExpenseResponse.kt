package com.dong.daytous.dto

import com.dong.daytous.domain.fixedexpense.Frequency
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class FixedExpenseResponse(
    val id: UUID,
    val description: String,
    val amount: BigDecimal,
    val frequency: Frequency,
    val startDate: LocalDate
)
