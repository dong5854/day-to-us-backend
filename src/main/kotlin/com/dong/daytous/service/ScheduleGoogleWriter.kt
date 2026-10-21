package com.dong.daytous.service

import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.schedule.SyncStatus
import com.dong.daytous.domain.user.SyncDirection
import com.dong.daytous.repository.ScheduleRepository
import com.dong.daytous.repository.SyncSettingRepository
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** The same policy is used for immediate writes and background retries. */
@Service
class ScheduleGoogleWriter(
    private val google: GoogleCalendarService,
    private val settings: SyncSettingRepository,
    private val schedules: ScheduleRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sync(schedule: Schedule) {
        val userId = schedule.googleUserId ?: schedule.createdBy
        val setting = settings.findByUserId(userId).orElse(null)
        if (setting != null && (!setting.syncEnabled || setting.syncDirection == SyncDirection.GOOGLE_TO_APP)) return
        // Existing event IDs are scoped to their original calendar. New events use the current selection.
        val calendarId = if (schedule.googleEventId != null) {
            schedule.googleCalendarId ?: setting?.googleCalendarId ?: "primary"
        } else setting?.googleCalendarId ?: "primary"
        try {
            val eventId = schedule.googleEventId
            when {
                schedule.deletedAt != null -> if (eventId != null) {
                    try { google.deleteEvent(userId, eventId, calendarId) }
                    catch (e: GoogleJsonResponseException) {
                        // 404 can also mean an inaccessible/wrong calendar. Only 410 confirms deletion.
                        if (e.statusCode != 410) throw e
                    }
                }
                eventId == null -> schedule.googleEventId = requireNotNull(google.pushEvent(userId, schedule, calendarId)) {
                    "Google calendar authentication is required"
                }
                else -> google.updateEvent(userId, eventId, schedule, calendarId)
            }
            schedule.googleUserId = userId
            schedule.googleCalendarId = calendarId
            schedule.syncStatus = SyncStatus.SYNCED
            schedule.lastSyncedAt = LocalDateTime.now()
        } catch (e: Exception) {
            schedule.syncStatus = SyncStatus.PENDING
            log.warn("Google sync pending for schedule {} ({})", schedule.id, e.javaClass.simpleName)
        }
        // Do not swallow optimistic-lock failures: stale writes must not undo a deletion.
        schedules.save(schedule)
    }
}
