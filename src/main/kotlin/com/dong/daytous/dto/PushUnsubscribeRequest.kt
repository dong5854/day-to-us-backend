package com.dong.daytous.dto

import jakarta.validation.constraints.NotBlank

data class PushUnsubscribeRequest(
    @field:NotBlank
    val endpoint: String,
)
