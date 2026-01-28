package com.dong.daytous.service

import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.dto.ScheduleRequest
import com.dong.daytous.dto.ScheduleResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.ScheduleRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

@Service
class ScheduleService(
    private val scheduleRepository: ScheduleRepository,
    private val sharedSpaceRepository: SharedSpaceRepository,
    private val userRepository: UserRepository,
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
            )

        return scheduleRepository.save(newSchedule).toResponse()
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
        checkAccessAndGetUser(spaceId, email)

        val schedule =
            scheduleRepository
                .findByIdOrNull(scheduleId)
                ?.takeIf { it.sharedSpace.id == spaceId }
                ?: throw EntityNotFoundException("Schedule not found")

        val updatedSchedule =
            Schedule(
                title = request.title,
                description = request.description,
                startDateTime = request.startDateTime,
                endDateTime = request.endDateTime,
                isAllDay = request.isAllDay,
                createdBy = schedule.createdBy, // 생성자는 유지
                sharedSpace = schedule.sharedSpace,
            ).apply { id = schedule.id } // ID 유지

        return scheduleRepository.save(updatedSchedule).toResponse()
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
                ?.takeIf { it.sharedSpace.id == spaceId }
                ?: throw EntityNotFoundException("Schedule not found")

        scheduleRepository.delete(schedule)
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
