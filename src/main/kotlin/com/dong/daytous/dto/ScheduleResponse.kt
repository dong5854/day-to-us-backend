package com.dong.daytous.dto

import java.time.LocalDateTime
import java.util.UUID

data class ScheduleResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val isAllDay: Boolean,
    val createdBy: Long
)
