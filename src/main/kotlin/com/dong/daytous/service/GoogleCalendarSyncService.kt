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
            .associateBy { it.googleEventId }

        for (event in googleEvents) {
            val eventId = event.id ?: continue
            if (event.status == "cancelled") {
                handleCancelledEvent(existingSchedules[eventId])
                continue
            }

            val existing = existingSchedules[eventId]
            if (existing != null) {
                mergeOrConflict(existing, event)
            } else {
                createScheduleFromGoogleEvent(event, userId, spaceId)
            }
        }
    }

    private fun handleCancelledEvent(schedule: Schedule?) {
        if (schedule != null) {
            scheduleRepository.delete(schedule)
            log.info("Deleted schedule {} (Google event cancelled)", schedule.id)
        }
    }

    private fun mergeOrConflict(schedule: Schedule, event: Event) {
        val googleUpdated = parseGoogleEventUpdated(event) ?: return
        val lastSynced = schedule.lastSyncedAt ?: LocalDateTime.MIN

        val googleModifiedSinceSync = googleUpdated.isAfter(lastSynced)
        val localModifiedSinceSync = schedule.lastModifiedAt.isAfter(lastSynced)

        if (googleModifiedSinceSync && localModifiedSinceSync) {
            // Both sides modified → CONFLICT
            schedule.syncStatus = SyncStatus.CONFLICT
            schedule.googleLastModifiedAt = googleUpdated
            scheduleRepository.save(schedule)
            log.info("Conflict detected for schedule {} (google event {})", schedule.id, event.id)
        } else if (googleModifiedSinceSync) {
            // Only Google modified → accept Google version
            applyGoogleEventToSchedule(schedule, event, googleUpdated)
        }
        // If only local modified (or neither), do nothing — push sync already handled it
    }

    private fun applyGoogleEventToSchedule(
        schedule: Schedule,
        event: Event,
        googleUpdated: LocalDateTime,
    ) {
        val (start, end, allDay) = parseEventTimes(event)

        val updated = Schedule(
            title = event.summary ?: schedule.title,
            description = event.description,
            startDateTime = start,
            endDateTime = end,
            isAllDay = allDay,
            createdBy = schedule.createdBy,
            sharedSpace = schedule.sharedSpace,
        ).apply {
            id = schedule.id
            googleEventId = schedule.googleEventId
            syncStatus = SyncStatus.SYNCED
            lastSyncedAt = LocalDateTime.now()
            lastModifiedAt = LocalDateTime.now()
            googleLastModifiedAt = googleUpdated
        }

        scheduleRepository.save(updated)
        log.debug("Updated schedule {} from Google event {}", schedule.id, event.id)
    }

    private fun createScheduleFromGoogleEvent(event: Event, userId: Long, spaceId: UUID) {
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
                val userId = schedule.createdBy
                val googleEventId = schedule.googleEventId

                if (googleEventId == null) {
                    // Never pushed — try to push
                    val newGoogleEventId = googleCalendarService.pushEvent(userId, schedule)
                    if (newGoogleEventId != null) {
                        schedule.googleEventId = newGoogleEventId
                        schedule.syncStatus = SyncStatus.SYNCED
                        schedule.lastSyncedAt = LocalDateTime.now()
                        scheduleRepository.save(schedule)
                        log.info("Retried push for schedule {} succeeded", schedule.id)
                    }
                } else {
                    // Was pushed before — try to update
                    googleCalendarService.updateEvent(userId, googleEventId, schedule)
                    schedule.syncStatus = SyncStatus.SYNCED
                    schedule.lastSyncedAt = LocalDateTime.now()
                    scheduleRepository.save(schedule)
                    log.info("Retried update for schedule {} succeeded", schedule.id)
                }
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
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zoneId)

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
