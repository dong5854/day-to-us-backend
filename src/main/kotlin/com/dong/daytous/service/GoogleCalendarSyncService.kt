package com.dong.daytous.service

import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.schedule.SyncStatus
import com.dong.daytous.domain.user.SyncDirection
import com.dong.daytous.repository.GoogleTokenRepository
import com.dong.daytous.repository.ScheduleRepository
import com.dong.daytous.repository.SyncSettingRepository
import com.dong.daytous.repository.UserRepository
import com.google.api.services.calendar.model.Event
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class GoogleCalendarSyncService(
    private val googleCalendarService: GoogleCalendarService,
    private val scheduleRepository: ScheduleRepository,
    private val userRepository: UserRepository,
    private val googleTokenRepository: GoogleTokenRepository,
    private val syncSettingRepository: SyncSettingRepository,
    private val googleWriter: ScheduleGoogleWriter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${google.calendar.sync.interval-ms:600000}")
    fun syncAllUsers() {
        val tokens = googleTokenRepository.findAll()
        for (token in tokens) {
            try {
                syncForUser(token.user.id)
            } catch (e: Exception) {
                log.warn("Sync failed for user {}: {}", token.user.id, e.message)
            }
        }

        retryPendingSchedules()
    }

    @Transactional
    fun syncForUser(userId: Long) {
        val setting = syncSettingRepository.findByUserId(userId).orElse(null)
        if (setting != null && (!setting.syncEnabled ||
                setting.syncDirection == SyncDirection.APP_TO_GOOGLE)) {
            return
        }

        val user = userRepository.findById(userId).orElse(null) ?: return
        val sharedSpace = user.sharedSpace ?: return
        val spaceId = sharedSpace.id ?: return

        val calendarId = setting?.googleCalendarId ?: "primary"
        val now = LocalDateTime.now()
        val timeMin = now.minusMonths(1)
        val timeMax = now.plusMonths(3)

        val googleEvents = googleCalendarService.pullEvents(userId, timeMin, timeMax, calendarId)
        if (googleEvents.isEmpty()) return

        val googleEventIds = googleEvents.mapNotNull { it.id }
        val existingSchedules = scheduleRepository
            .findBySharedSpaceIdAndGoogleEventIdIn(spaceId, googleEventIds)
            .filter { it.googleCalendarId == null || (it.googleCalendarId == calendarId &&
                (calendarId != "primary" || (it.googleUserId ?: it.createdBy) == userId)) }
            .associateBy { it.googleEventId }

        for (event in googleEvents) {
            val eventId = event.id ?: continue
            val existing = existingSchedules[eventId]
            if (existing?.deletedAt != null) {
                // A legacy tombstone has no known origin until a real pull confirms it.
                if (existing.googleCalendarId == null || existing.googleUserId == null) {
                    existing.googleCalendarId = calendarId
                    existing.googleUserId = existing.googleUserId ?: userId
                    scheduleRepository.save(existing)
                }
                continue
            }
            if (event.status == "cancelled") {
                handleCancelledEvent(existingSchedules[eventId])
                continue
            }

            if (existing != null) {
                val originChanged = existing.googleUserId == null || existing.googleCalendarId == null
                existing.googleUserId = existing.googleUserId ?: userId
                existing.googleCalendarId = existing.googleCalendarId ?: calendarId
                mergeOrConflict(existing, event, originChanged)
            } else {
                createScheduleFromGoogleEvent(event, userId, spaceId, calendarId)
            }
        }
    }

    private fun handleCancelledEvent(schedule: Schedule?) {
        if (schedule != null) {
            schedule.deletedAt = LocalDateTime.now()
            schedule.syncStatus = SyncStatus.SYNCED
            scheduleRepository.save(schedule)
            log.info("Deleted schedule {} (Google event cancelled)", schedule.id)
        }
    }

    private fun mergeOrConflict(schedule: Schedule, event: Event, originChanged: Boolean) {
        val googleUpdated = parseGoogleEventUpdated(event)
        if (googleUpdated == null) {
            if (originChanged) scheduleRepository.save(schedule)
            return
        }
        val lastSynced = schedule.lastSyncedAt ?: LocalDateTime.MIN
        val (parsedStart, parsedEnd, _) = parseEventTimes(event)

        val timeMismatch = schedule.startDateTime != parsedStart || schedule.endDateTime != parsedEnd
        val googleModifiedSinceSync = googleUpdated.isAfter(lastSynced)
        val localModifiedSinceSync = schedule.lastModifiedAt.isAfter(lastSynced)

        // A pull of unchanged remote data must not overwrite a local edit awaiting retry.
        if (schedule.syncStatus == SyncStatus.PENDING && !googleModifiedSinceSync) {
            if (originChanged) scheduleRepository.save(schedule)
            return
        }
        if (googleModifiedSinceSync && localModifiedSinceSync) {
            schedule.syncStatus = SyncStatus.CONFLICT
            schedule.googleLastModifiedAt = googleUpdated
            scheduleRepository.save(schedule)
            log.info("Conflict detected for schedule {} (google event {})", schedule.id, event.id)
        } else if (googleModifiedSinceSync || timeMismatch) {
            applyGoogleEventToSchedule(schedule, event, googleUpdated)
        } else if (originChanged) {
            scheduleRepository.save(schedule)
        }
    }

    private fun applyGoogleEventToSchedule(
        schedule: Schedule,
        event: Event,
        googleUpdated: LocalDateTime,
    ) {
        val (start, end, allDay) = parseEventTimes(event)

        schedule.title = event.summary ?: schedule.title
        schedule.description = event.description
        schedule.startDateTime = start
        schedule.endDateTime = end
        schedule.isAllDay = allDay
        schedule.syncStatus = SyncStatus.SYNCED
        schedule.lastSyncedAt = LocalDateTime.now()
        schedule.lastModifiedAt = LocalDateTime.now()
        schedule.googleLastModifiedAt = googleUpdated
        scheduleRepository.save(schedule)
        log.debug("Updated schedule {} from Google event {}", schedule.id, event.id)
    }

    private fun createScheduleFromGoogleEvent(event: Event, userId: Long, spaceId: UUID, calendarId: String) {
        val user = userRepository.findById(userId).orElse(null) ?: return
        val sharedSpace = user.sharedSpace ?: return
        if (sharedSpace.id != spaceId) return

        val (start, end, allDay) = parseEventTimes(event)
        val googleUpdated = parseGoogleEventUpdated(event)

        val schedule = Schedule(
            title = event.summary ?: "(No title)",
            description = event.description,
            startDateTime = start,
            endDateTime = end,
            isAllDay = allDay,
            createdBy = userId,
            sharedSpace = sharedSpace,
        ).apply {
            googleEventId = event.id
            googleUserId = userId
            googleCalendarId = calendarId
            syncStatus = SyncStatus.SYNCED
            lastSyncedAt = LocalDateTime.now()
            lastModifiedAt = LocalDateTime.now()
            googleLastModifiedAt = googleUpdated
        }

        scheduleRepository.save(schedule)
        log.debug("Created schedule from Google event {}", event.id)
    }

    private fun retryPendingSchedules() {
        val pendingSchedules = scheduleRepository.findBySyncStatus(SyncStatus.PENDING)
        for (schedule in pendingSchedules) {
            try {
                googleWriter.sync(schedule)
            } catch (e: Exception) {
                log.warn("Retry sync for schedule {} failed: {}", schedule.id, e.message)
            }
        }
    }

    private fun parseEventTimes(event: Event): EventTimes {
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

    private fun parseGoogleEventUpdated(event: Event): LocalDateTime? {
        val updated = event.updated ?: return null
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(updated.value),
            ZoneId.systemDefault(),
        )
    }

    private data class EventTimes(
        val start: LocalDateTime,
        val end: LocalDateTime,
        val isAllDay: Boolean,
    )
}
