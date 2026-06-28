package com.dong.daytous.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

data class PushSubscribeRequest(
    @field:NotBlank
    val endpoint: String,

    @field:Valid
    val keys: PushKeys,
)

data class PushKeys(
    @field:NotBlank
    val p256dh: String,

    @field:NotBlank
    val auth: String,
)
