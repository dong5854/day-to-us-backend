package com.dong.daytous.service

import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.schedule.SyncStatus
import com.dong.daytous.dto.ScheduleRequest
import com.dong.daytous.dto.ScheduleResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.ScheduleRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import com.dong.daytous.repository.SyncSettingRepository
import jakarta.persistence.EntityNotFoundException
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
    private val googleWriter: ScheduleGoogleWriter,
) {

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

        googleWriter.sync(saved)

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
        val endExclusive = YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay()

        return scheduleRepository
            .findOverlappingMonth(spaceId, startOfMonth, endExclusive)
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
                ?.takeIf { it.sharedSpace.id == spaceId && it.deletedAt == null }
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
        checkAccessAndGetUser(spaceId, email)

        val schedule =
            scheduleRepository
                .findByIdOrNull(scheduleId)
                ?.takeIf { it.sharedSpace.id == spaceId && it.deletedAt == null }
                ?: throw EntityNotFoundException("Schedule not found")

        schedule.title = request.title
        schedule.description = request.description
        schedule.startDateTime = request.startDateTime
        schedule.endDateTime = request.endDateTime
        schedule.isAllDay = request.isAllDay
        schedule.lastModifiedAt = LocalDateTime.now()
        val saved = scheduleRepository.save(schedule)

        googleWriter.sync(saved)

        return saved.toResponse()
    }

    @Transactional
    fun deleteSchedule(
        spaceId: UUID,
        scheduleId: UUID,
        email: String,
    ) {
        checkAccessAndGetUser(spaceId, email)

        val schedule =
            scheduleRepository
                .findByIdOrNull(scheduleId)
                ?.takeIf { it.sharedSpace.id == spaceId && it.deletedAt == null }
                ?: throw EntityNotFoundException("Schedule not found")

        schedule.deletedAt = LocalDateTime.now()
        schedule.syncStatus = SyncStatus.PENDING
        scheduleRepository.save(schedule)
        googleWriter.sync(schedule)
    }

    @Transactional
    fun resolveConflict(
        spaceId: UUID,
        scheduleId: UUID,
        resolution: ConflictResolution,
        email: String,
    ): ScheduleResponse {
        checkAccessAndGetUser(spaceId, email)

        val schedule =
            scheduleRepository
                .findByIdOrNull(scheduleId)
                ?.takeIf { it.sharedSpace.id == spaceId && it.deletedAt == null }
                ?: throw EntityNotFoundException("Schedule not found")

        if (schedule.syncStatus != SyncStatus.CONFLICT) {
            throw IllegalStateException("Schedule is not in CONFLICT state")
        }

        when (resolution) {
            ConflictResolution.KEEP_LOCAL -> {
                // Push local version to Google
                googleWriter.sync(schedule)
            }
            ConflictResolution.KEEP_GOOGLE -> {
                // Pull Google version — re-fetch the single event
                val googleEventId = schedule.googleEventId
                    ?: throw IllegalStateException("No Google event ID for conflict resolution")
                val events = googleCalendarService.pullEvents(
                    schedule.googleUserId ?: schedule.createdBy,
                    schedule.startDateTime.minusDays(1),
                    schedule.endDateTime.plusDays(1),
                    schedule.googleCalendarId ?: getCalendarId(schedule.googleUserId ?: schedule.createdBy),
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

                schedule.title = googleEvent.summary ?: schedule.title
                schedule.description = googleEvent.description
                schedule.startDateTime = start
                schedule.endDateTime = if (allDay) end.minusDays(1) else end
                schedule.isAllDay = allDay
                schedule.syncStatus = SyncStatus.SYNCED
                schedule.lastSyncedAt = LocalDateTime.now()
                schedule.lastModifiedAt = LocalDateTime.now()
                schedule.googleLastModifiedAt = LocalDateTime.now()

                return scheduleRepository.save(schedule).toResponse()
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
            .findBySharedSpaceIdAndSyncStatusAndDeletedAtIsNull(spaceId, SyncStatus.CONFLICT)
            .map { it.toResponse() }
    }

    private fun getCalendarId(userId: Long): String =
        syncSettingRepository.findByUserId(userId).orElse(null)?.googleCalendarId ?: "primary"

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
