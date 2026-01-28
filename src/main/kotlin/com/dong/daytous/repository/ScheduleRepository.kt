package com.dong.daytous.repository

import com.dong.daytous.domain.schedule.Schedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface ScheduleRepository : JpaRepository<Schedule, UUID> {
    // 특정 공간의 특정 기간 내 일정 조회 (시작일 또는 종료일이 기간 내에 포함되는 경우)
    fun findBySharedSpaceIdAndStartDateTimeBetweenOrderByStartDateTime(
        sharedSpaceId: UUID,
        start: LocalDateTime,
        end: LocalDateTime
    ): List<Schedule>
    
    // 단순히 공간의 모든 일정 조회 (필요 시 사용)
    fun findBySharedSpaceIdOrderByStartDateTime(sharedSpaceId: UUID): List<Schedule>
}
