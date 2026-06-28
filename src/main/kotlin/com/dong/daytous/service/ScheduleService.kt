package com.dong.daytous.service

import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.schedule.SyncStatus
import com.dong.daytous.dto.ScheduleRequest
import com.dong.daytous.dto.ScheduleResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.ScheduleRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import com.dong.daytous.domain.user.SyncDirection
import com.dong.daytous.repository.SyncSettingRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

enum class ConflictResolution {
    KEEP_LOCAL,
    KEEP_GOOGLE,
}

@Service
class ScheduleService(
    private val scheduleRepository: ScheduleRepository,
    private val sharedSpaceRepository: SharedSpaceRepository,
    private val userRepository: UserRepository,
    private val googleCalendarService: GoogleCalendarService,
    private val syncSettingRepository: SyncSettingRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createSchedule(
        spaceId: UUID,
        request: ScheduleRequest,
        email: String,
    ): ScheduleResponse {
        val user = checkAccessAndGetUser(spaceId, email)

        val sharedSpace =
            sharedSpaceRepository
                .findById(spaceId)
                .orElseThrow { EntityNotFoundException("SharedSpace with id $spaceId not found") }

        val newSchedule =
            Schedule(
                title = request.title,
                description = request.description,
                startDateTime = request.startDateTime,
                endDateTime = request.endDateTime,
                isAllDay = request.isAllDay,
                createdBy = user.id,
                sharedSpace = sharedSpace,
            ).apply {
                lastModifiedAt = LocalDateTime.now()
            }

        val saved = scheduleRepository.save(newSchedule)

        syncPushToGoogle(user.id, saved)

        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun getSchedules(
        spaceId: UUID,
        year: Int,
        month: Int,
        email: String,
    ): List<ScheduleResponse> {
        checkAccessAndGetUser(spaceId, email)

        val startOfMonth = LocalDateTime.of(LocalDate.of(year, month, 1), LocalTime.MIN)
        val endOfMonth = LocalDateTime.of(YearMonth.of(year, month).atEndOfMonth(), LocalTime.MAX)

        return scheduleRepository
            .findBySharedSpaceIdAndStartDateTimeBetweenOrderByStartDateTime(spaceId, startOfMonth, endOfMonth)
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getScheduleById(
        spaceId: UUID,
        scheduleId: UUID,
        email: String,
    ): ScheduleResponse {
        checkAccessAndGetUser(spaceId, email)

        val schedule =
            scheduleRepository
                .findByIdOrNull(scheduleId)
                ?.takeIf { it.sharedSpace.id == spaceId }
                ?: throw EntityNotFoundException("Schedule not found")

        return schedule.toResponse()
    }

    @Transactional
    fun updateSchedule(
        spaceId: UUID,
        scheduleId: UUID,
        request: ScheduleRequest,
        email: String,
    ): ScheduleResponse {
        val user = checkAccessAndGetUser(spaceId, email)

        val schedule =
            scheduleRepository
                .findByIdOrNull(scheduleId)
                ?.takeIf { it.sharedSpace.id == spaceId }
                ?: throw EntityNotFoundException("Schedule not found")

        val now = LocalDateTime.now()

        val updatedSchedule =
            Schedule(
                title = request.title,
                description = request.description,
                startDateTime = request.startDateTime,
                endDateTime = request.endDateTime,
                isAllDay = request.isAllDay,
                createdBy = schedule.createdBy,
                sharedSpace = schedule.sharedSpace,
            ).apply {
                id = schedule.id
                googleEventId = schedule.googleEventId
                syncStatus = schedule.syncStatus
                lastSyncedAt = schedule.lastSyncedAt
                lastModifiedAt = now
                googleLastModifiedAt = schedule.googleLastModifiedAt
            }

        val saved = scheduleRepository.save(updatedSchedule)

        syncPushUpdateToGoogle(user.id, saved)

        return saved.toResponse()
    }

    @Transactional
    fun deleteSchedule(
        spaceId: UUID,
        scheduleId: UUID,
        email: String,
    ) {
        val user = checkAccessAndGetUser(spaceId, email)

        val schedule =
            scheduleRepository
                .findByIdOrNull(scheduleId)
                ?.takeIf { it.sharedSpace.id == spaceId }
                ?: throw EntityNotFoundException("Schedule not found")

        if (canPushToGoogle(user.id)) {
            val calendarId = getCalendarId(user.id)
            schedule.googleEventId?.let { googleEventId ->
                try {
                    googleCalendarService.deleteEvent(user.id, googleEventId, calendarId)
                } catch (e: Exception) {
                    log.warn("Failed to delete Google Calendar event {}: {}", googleEventId, e.message)
                }
            }
        }

        scheduleRepository.delete(schedule)
    }

    @Transactional
    fun resolveConflict(
        spaceId: UUID,
        scheduleId: UUID,
        resolution: ConflictResolution,
        email: String,
    ): ScheduleResponse {
        val user = checkAccessAndGetUser(spaceId, email)

        val schedule =
            scheduleRepository
                .findByIdOrNull(scheduleId)
                ?.takeIf { it.sharedSpace.id == spaceId }
                ?: throw EntityNotFoundException("Schedule not found")

        if (schedule.syncStatus != SyncStatus.CONFLICT) {
            throw IllegalStateException("Schedule is not in CONFLICT state")
        }

        when (resolution) {
            ConflictResolution.KEEP_LOCAL -> {
                // Push local version to Google
                syncPushUpdateToGoogle(user.id, schedule)
            }
            ConflictResolution.KEEP_GOOGLE -> {
                // Pull Google version — re-fetch the single event
                val googleEventId = schedule.googleEventId
                    ?: throw IllegalStateException("No Google event ID for conflict resolution")
                val events = googleCalendarService.pullEvents(
                    user.id,
                    schedule.startDateTime.minusDays(1),
                    schedule.endDateTime.plusDays(1),
                )
                val googleEvent = events.find { it.id == googleEventId }
                    ?: throw IllegalStateException("Google event not found: $googleEventId")

                val startMillis = googleEvent.start?.dateTime?.value
                    ?: googleEvent.start?.date?.value ?: 0L
                val endMillis = googleEvent.end?.dateTime?.value
                    ?: googleEvent.end?.date?.value ?: 0L
                val zoneId = java.time.ZoneId.systemDefault()
                val start = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startMillis), zoneId)
                val end = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endMillis), zoneId)
                val allDay = googleEvent.start?.date != null

                val resolved = Schedule(
                    title = googleEvent.summary ?: schedule.title,
                    description = googleEvent.description,
                    startDateTime = start,
                    endDateTime = end,
                    isAllDay = allDay,
                    createdBy = schedule.createdBy,
                    sharedSpace = schedule.sharedSpace,
                ).apply {
                    id = schedule.id
                    this.googleEventId = schedule.googleEventId
                    syncStatus = SyncStatus.SYNCED
                    lastSyncedAt = LocalDateTime.now()
                    lastModifiedAt = LocalDateTime.now()
                    googleLastModifiedAt = LocalDateTime.now()
                }

                return scheduleRepository.save(resolved).toResponse()
            }
        }

        return schedule.toResponse()
    }

    @Transactional(readOnly = true)
    fun getConflicts(
        spaceId: UUID,
        email: String,
    ): List<ScheduleResponse> {
        checkAccessAndGetUser(spaceId, email)

        return scheduleRepository
            .findBySharedSpaceIdAndSyncStatus(spaceId, SyncStatus.CONFLICT)
            .map { it.toResponse() }
    }

    private fun canPushToGoogle(userId: Long): Boolean {
        val setting = syncSettingRepository.findByUserId(userId).orElse(null) ?: return true
        return setting.syncEnabled &&
            setting.syncDirection in listOf(SyncDirection.BIDIRECTIONAL, SyncDirection.APP_TO_GOOGLE)
    }

    private fun getCalendarId(userId: Long): String {
        return syncSettingRepository.findByUserId(userId).orElse(null)?.googleCalendarId ?: "primary"
    }

    private fun syncPushToGoogle(userId: Long, schedule: Schedule) {
        if (!canPushToGoogle(userId)) return
        val calendarId = getCalendarId(userId)
        try {
            val googleEventId = googleCalendarService.pushEvent(userId, schedule, calendarId)
            if (googleEventId != null) {
                schedule.googleEventId = googleEventId
                schedule.syncStatus = SyncStatus.SYNCED
                schedule.lastSyncedAt = LocalDateTime.now()
                scheduleRepository.save(schedule)
            }
        } catch (e: Exception) {
            log.warn("Failed to push schedule {} to Google Calendar: {}", schedule.id, e.message)
            schedule.syncStatus = SyncStatus.PENDING
            scheduleRepository.save(schedule)
        }
    }

    private fun syncPushUpdateToGoogle(userId: Long, schedule: Schedule) {
        if (!canPushToGoogle(userId)) return
        val calendarId = getCalendarId(userId)
        val googleEventId = schedule.googleEventId ?: return
        try {
            googleCalendarService.updateEvent(userId, googleEventId, schedule, calendarId)
            schedule.syncStatus = SyncStatus.SYNCED
            schedule.lastSyncedAt = LocalDateTime.now()
            scheduleRepository.save(schedule)
        } catch (e: Exception) {
            log.warn("Failed to update Google Calendar event {}: {}", googleEventId, e.message)
            schedule.syncStatus = SyncStatus.PENDING
            scheduleRepository.save(schedule)
        }
    }

    private fun checkAccessAndGetUser(
        spaceId: UUID,
        email: String,
    ): com.dong.daytous.domain.user.User {
        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { EntityNotFoundException("User not found") }

        if (user.sharedSpace?.id != spaceId) {
            throw IllegalArgumentException("Access denied: User does not belong to this shared space")
        }
        return user
    }
}
