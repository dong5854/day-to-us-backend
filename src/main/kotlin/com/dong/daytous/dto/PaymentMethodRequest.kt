package com.dong.daytous.dto

import jakarta.validation.constraints.NotBlank

data class PaymentMethodRequest(
    @field:NotBlank
    val name: String
)
