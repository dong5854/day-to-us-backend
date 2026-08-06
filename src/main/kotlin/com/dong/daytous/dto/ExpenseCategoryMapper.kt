package com.dong.daytous.dto

import com.dong.daytous.domain.category.ExpenseCategory

fun ExpenseCategory.toResponse(): ExpenseCategoryResponse =
    ExpenseCategoryResponse(
        id = this.id ?: throw IllegalStateException("ExpenseCategory ID cannot be null"),
        name = this.name
    )
