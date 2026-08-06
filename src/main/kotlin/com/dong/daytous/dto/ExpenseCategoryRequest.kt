package com.dong.daytous.dto

import jakarta.validation.constraints.NotBlank

data class ExpenseCategoryRequest(
    @field:NotBlank
    val name: String
)
