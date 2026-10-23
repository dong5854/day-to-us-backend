package com.dong.daytous.dto

import java.math.BigDecimal
import java.util.UUID

data class CategoryBudgetRequest(val amount: Long?, val repeat: Boolean = false)
data class CategoryOrderRequest(val ids: List<UUID>)
data class CategoryArchiveRequest(val archived: Boolean)
data class CategoryMonthTotal(val month: String, val spent: BigDecimal, val budget: Long?)
data class CategoryOverviewRow(
    val category: ExpenseCategoryResponse?,
    val history: List<CategoryMonthTotal>,
    val used: Boolean,
    val fixedExpenseIds: List<UUID>,
)
data class CategoryOverview(val month: String, val totalSpent: BigDecimal, val rows: List<CategoryOverviewRow>)
