package com.dong.daytous.dto

import com.dong.daytous.domain.fixedexpense.Frequency
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class FixedExpenseRequest(
    @field:NotBlank
    val description: String,

    @field:NotNull
    @field:DecimalMin("0.0")
    val amount: BigDecimal,

    @field:NotNull
    val frequency: Frequency,

    @field:NotNull
    val startDate: LocalDate
)
