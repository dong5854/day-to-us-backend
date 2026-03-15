package com.dong.daytous.dto

import com.dong.daytous.domain.push.PushSubscription
import java.time.LocalDateTime
import java.util.UUID

data class PushSubscriptionResponse(
    val id: UUID,
    val endpoint: String,
    val createdAt: LocalDateTime,
)

fun PushSubscription.toResponse() = PushSubscriptionResponse(
    id = id!!,
    endpoint = endpoint,
    createdAt = createdAt,
)
