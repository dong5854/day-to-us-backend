package com.dong.daytous.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class ScheduleRequest(
    @field:NotBlank
    val title: String,

    val description: String? = null,

    @field:NotNull
    val startDateTime: LocalDateTime,

    @field:NotNull
    val endDateTime: LocalDateTime,

    val isAllDay: Boolean = false
)
