package com.dong.daytous.controller

import com.dong.daytous.dto.ScheduleRequest
import com.dong.daytous.dto.ScheduleResponse
import com.dong.daytous.service.ScheduleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/shared-spaces/{spaceId}/schedules")
class ScheduleController(
    private val scheduleService: ScheduleService,
) {
    @PostMapping
    fun createSchedule(
        @PathVariable spaceId: UUID,
        @Valid @RequestBody request: ScheduleRequest,
        principal: Principal,
    ): ResponseEntity<ScheduleResponse> {
        val createdSchedule = scheduleService.createSchedule(spaceId, request, principal.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSchedule)
    }

    @GetMapping
    fun getSchedules(
        @PathVariable spaceId: UUID,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        principal: Principal,
    ): List<ScheduleResponse> {
        val currentDate = LocalDate.now()
        val targetYear = year ?: currentDate.year
        val targetMonth = month ?: currentDate.monthValue
        return scheduleService.getSchedules(spaceId, targetYear, targetMonth, principal.name)
    }

    @GetMapping("/{scheduleId}")
    fun getScheduleById(
        @PathVariable spaceId: UUID,
        @PathVariable scheduleId: UUID,
        principal: Principal,
    ): ResponseEntity<ScheduleResponse> {
        val schedule = scheduleService.getScheduleById(spaceId, scheduleId, principal.name)
        return ResponseEntity.ok(schedule)
    }

    @PutMapping("/{scheduleId}")
    fun updateSchedule(
        @PathVariable spaceId: UUID,
        @PathVariable scheduleId: UUID,
        @Valid @RequestBody request: ScheduleRequest,
        principal: Principal,
    ): ResponseEntity<ScheduleResponse> {
        val updatedSchedule = scheduleService.updateSchedule(spaceId, scheduleId, request, principal.name)
        return ResponseEntity.ok(updatedSchedule)
    }

    @DeleteMapping("/{scheduleId}")
    fun deleteSchedule(
        @PathVariable spaceId: UUID,
        @PathVariable scheduleId: UUID,
        principal: Principal,
    ): ResponseEntity<Void> {
        scheduleService.deleteSchedule(spaceId, scheduleId, principal.name)
        return ResponseEntity.noContent().build()
    }
}
