package com.dong.daytous.dto

import com.dong.daytous.domain.fixedexpense.FixedExpense

fun FixedExpense.toResponse(): FixedExpenseResponse {
    return FixedExpenseResponse(
        id = this.id!!,
        description = this.description,
        amount = this.amount,
        frequency = this.frequency,
        startDate = this.startDate
    )
}
