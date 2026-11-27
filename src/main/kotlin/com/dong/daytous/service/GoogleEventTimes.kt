package com.dong.daytous.service

import com.google.api.services.calendar.model.Event
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

internal data class EventTimes(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean,
)

internal fun parseEventTimes(event: Event): EventTimes {
    val zoneId = ZoneId.systemDefault()

    val isAllDay = event.start?.date != null

    val startMillis = event.start?.dateTime?.value ?: event.start?.date?.value ?: 0L
    val endMillis = event.end?.dateTime?.value ?: event.end?.date?.value ?: 0L

    val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), zoneId)
    var end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zoneId)

    if (isAllDay) {
        end = end.minusDays(1)
    }

    return EventTimes(start, end, isAllDay)
}
