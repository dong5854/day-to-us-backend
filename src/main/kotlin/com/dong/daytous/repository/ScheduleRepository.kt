package com.dong.daytous.repository

import com.dong.daytous.domain.schedule.Schedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface ScheduleRepository : JpaRepository<Schedule, UUID> {
    // Timed events have an exclusive end; all-day events store the last included day.
    @Query("""
        SELECT s FROM Schedule s
        WHERE s.sharedSpace.id = :spaceId AND s.deletedAt IS NULL
          AND s.startDateTime < :endExclusive
          AND (s.endDateTime > :start
            OR (s.isAllDay = true AND s.endDateTime >= :start)
            OR (s.startDateTime = s.endDateTime AND s.startDateTime >= :start))
        ORDER BY s.startDateTime
    """)
    fun findOverlappingMonth(
        @Param("spaceId") sharedSpaceId: UUID,
        @Param("start") start: LocalDateTime,
        @Param("endExclusive") endExclusive: LocalDateTime,
    ): List<Schedule>
    
    // Include tombstones here so pulls can suppress deleted remote events.
    fun findBySharedSpaceIdAndGoogleEventIdIn(sharedSpaceId: UUID, googleEventIds: List<String>): List<Schedule>

    fun findBySharedSpaceIdAndSyncStatusAndDeletedAtIsNull(sharedSpaceId: UUID, syncStatus: com.dong.daytous.domain.schedule.SyncStatus): List<Schedule>

    fun findBySyncStatus(syncStatus: com.dong.daytous.domain.schedule.SyncStatus): List<Schedule>

    fun findByStartDateTimeBetweenAndDeletedAtIsNull(start: LocalDateTime, end: LocalDateTime): List<Schedule>

    @Query("""
        SELECT s FROM Schedule s
        JOIN FETCH s.sharedSpace
        WHERE s.startDateTime BETWEEN :start AND :end AND s.deletedAt IS NULL
    """)
    fun findByStartDateTimeBetweenWithSharedSpace(
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<Schedule>
}
