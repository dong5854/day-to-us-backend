package com.dong.daytous.dto

import com.dong.daytous.domain.fixedexpense.Frequency
import com.dong.daytous.domain.fixedexpense.FixedTransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class FixedExpenseResponse(
    val id: UUID,
    val description: String,
    val amount: BigDecimal,
    val frequency: Frequency,
    val startDate: LocalDate,
    val categoryId: UUID? = null,
    val paymentMethodId: UUID? = null,
    val type: FixedTransactionType = FixedTransactionType.EXPENSE,
    val autoPostFrom: LocalDate = LocalDate.of(2026, 6, 1)
)
