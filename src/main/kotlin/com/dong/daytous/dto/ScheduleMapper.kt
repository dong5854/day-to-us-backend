package com.dong.daytous.dto

import com.dong.daytous.domain.schedule.Schedule

fun Schedule.toResponse(): ScheduleResponse {
    return ScheduleResponse(
        id = this.id!!,
        title = this.title,
        description = this.description,
        startDateTime = this.startDateTime,
        endDateTime = this.endDateTime,
        isAllDay = this.isAllDay,
        createdBy = this.createdBy
    )
}
