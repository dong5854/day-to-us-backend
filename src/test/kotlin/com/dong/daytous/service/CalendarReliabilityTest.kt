package com.dong.daytous.service

import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.schedule.SyncStatus
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.*
import com.dong.daytous.repository.*
import com.google.api.services.calendar.model.Event
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
@Import(ScheduleService::class, GoogleCalendarSyncService::class, ScheduleGoogleWriter::class)
class CalendarReliabilityTest {
    @Autowired lateinit var schedules: ScheduleRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var spaces: SharedSpaceRepository
    @Autowired lateinit var settings: SyncSettingRepository
    @Autowired lateinit var service: ScheduleService
    @Autowired lateinit var sync: GoogleCalendarSyncService
    @Autowired lateinit var writer: ScheduleGoogleWriter
    @Autowired lateinit var em: EntityManager
    @MockitoBean lateinit var google: GoogleCalendarService
    private lateinit var space: SharedSpace
    private lateinit var user: User

    @BeforeEach
    fun setup() {
        space = spaces.saveAndFlush(SharedSpace(name = "Calendar test"))
        val key = UUID.randomUUID().toString()
        user = users.saveAndFlush(User(name = "Test", email = "$key@test.com", role = Role.USER,
            provider = "google", providerId = key, sharedSpace = space))
        settings.saveAndFlush(SyncSetting(user = user, googleCalendarId = "couple-calendar"))
    }

    private fun add(title: String, start: String, end: String, allDay: Boolean = false) = schedules.saveAndFlush(
        Schedule(title, startDateTime = LocalDateTime.parse(start), endDateTime = LocalDateTime.parse(end),
            isAllDay = allDay, createdBy = user.id, sharedSpace = space))

    @Test
    fun `월 조회는 앞뒤 월과 겹치는 일정 및 한 달 전체를 감싸는 일정을 포함한다`() {
        add("이전 달 시작", "2026-10-30T12:00:00", "2026-11-02T12:00:00")
        add("다음 달 종료", "2026-11-30T12:00:00", "2026-12-02T12:00:00")
        add("한 달 전체 포함", "2026-10-01T12:00:00", "2026-12-31T12:00:00")
        add("이번 달", "2026-11-15T12:00:00", "2026-11-15T13:00:00")
        add("이전 달만", "2026-10-01T00:00:00", "2026-10-31T23:00:00")
        add("다음 달 시작", "2026-12-01T00:00:00", "2026-12-01T01:00:00")
        assertThat(service.getSchedules(space.id!!, 2026, 11, user.email).map { it.title })
            .containsExactly("한 달 전체 포함", "이전 달 시작", "이번 달", "다음 달 종료")
    }

    @Test
    fun `자정에 끝난 시간 일정은 제외하고 그날까지인 종일 및 순간 일정은 포함한다`() {
        add("자정에 종료", "2026-10-31T23:00:00", "2026-11-01T00:00:00")
        add("1일까지 종일", "2026-10-31T00:00:00", "2026-11-01T00:00:00", true)
        add("자정 순간 일정", "2026-11-01T00:00:00", "2026-11-01T00:00:00")
        assertThat(service.getSchedules(space.id!!, 2026, 11, user.email).map { it.title })
            .containsExactly("1일까지 종일", "자정 순간 일정")
    }

    @Test
    fun `다른 공간 일정은 겹쳐도 조회되지 않는다`() {
        val other = spaces.saveAndFlush(SharedSpace(name = "Other"))
        schedules.saveAndFlush(Schedule("비공개", startDateTime = LocalDateTime.of(2026, 11, 1, 0, 0),
            endDateTime = LocalDateTime.of(2026, 11, 2, 0, 0), createdBy = user.id, sharedSpace = other))
        assertThat(service.getSchedules(space.id!!, 2026, 11, user.email)).isEmpty()
    }

    @Test
    fun `삭제 실패 후 재수신해도 목록과 알림에 없고 재시도 성공 뒤에도 되살아나지 않는다`() {
        val row = add("삭제할 일정", "2026-11-01T09:00:00", "2026-11-01T10:00:00")
        row.googleEventId = "remote-id"
        row.googleUserId = user.id
        row.googleCalendarId = "couple-calendar"
        schedules.saveAndFlush(row)
        doThrow(RuntimeException("offline")).whenever(google).deleteEvent(user.id, "remote-id", "couple-calendar")
        service.deleteSchedule(space.id!!, row.id!!, user.email)
        assertThat(row.syncStatus).isEqualTo(SyncStatus.PENDING)
        assertThat(service.getSchedules(space.id!!, 2026, 11, user.email)).isEmpty()
        assertThatThrownBy { service.getScheduleById(space.id!!, row.id!!, user.email) }
            .isInstanceOf(EntityNotFoundException::class.java)
        assertThat(schedules.findByStartDateTimeBetweenWithSharedSpace(
            LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 2, 0, 0))).isEmpty()
        whenever(google.pullEvents(eq(user.id), any(), any(), eq("couple-calendar")))
            .thenReturn(listOf(Event().setId("remote-id").setStatus("confirmed")))
        sync.syncForUser(user.id)
        assertThat(schedules.count()).isEqualTo(1)
        doNothing().whenever(google).deleteEvent(user.id, "remote-id", "couple-calendar")
        sync.syncAllUsers()
        assertThat(row.syncStatus).isEqualTo(SyncStatus.SYNCED)
        sync.syncForUser(user.id)
        assertThat(service.getSchedules(space.id!!, 2026, 11, user.email)).isEmpty()
        assertThat(schedules.count()).isEqualTo(1)
        verify(google, times(2)).deleteEvent(user.id, "remote-id", "couple-calendar")
    }
    @Test
    fun `삭제 이전에 읽은 오래된 일정은 나중에 저장해도 삭제를 취소하지 못한다`() {
        val stale = add("오래된 복사본", "2026-11-01T09:00:00", "2026-11-01T10:00:00")
        em.detach(stale)
        service.deleteSchedule(space.id!!, stale.id!!, user.email)
        schedules.flush()
        em.clear()
        stale.title = "늦게 도착한 동기화"
        assertThatThrownBy { schedules.saveAndFlush(stale) }
            .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException::class.java)
    }

    @Test
    fun `구버전 삭제 흔적은 실제 가져온 캘린더를 기록하되 복원하지 않는다`() {
        val row = add("이전 삭제", "2026-11-01T09:00:00", "2026-11-01T10:00:00")
        row.googleEventId = "legacy"
        row.deletedAt = LocalDateTime.now()
        row.syncStatus = SyncStatus.PENDING
        schedules.saveAndFlush(row)
        whenever(google.pullEvents(eq(user.id), any(), any(), eq("couple-calendar")))
            .thenReturn(listOf(Event().setId("legacy").setStatus("confirmed")))
        sync.syncForUser(user.id)
        assertThat(row.googleCalendarId).isEqualTo("couple-calendar")
        assertThat(row.googleUserId).isEqualTo(user.id)
        assertThat(row.deletedAt).isNotNull()
        assertThat(row.syncStatus).isEqualTo(SyncStatus.PENDING)
        assertThat(schedules.count()).isEqualTo(1)
    }

    @Test
    fun `변경되지 않은 원격 데이터로 수정 재시도 대기 중인 로컬 내용을 덮어쓰지 않는다`() {
        val row = add("사용자 수정", "2026-11-01T11:00:00", "2026-11-01T12:00:00")
        row.googleEventId = "pending-edit"
        row.syncStatus = SyncStatus.PENDING
        row.lastSyncedAt = LocalDateTime.now().minusHours(1)
        row.lastModifiedAt = LocalDateTime.now()
        schedules.saveAndFlush(row)
        val remote = Event().setId("pending-edit").setSummary("이전 제목")
            .setUpdated(com.google.api.client.util.DateTime(0L))
            .setStart(com.google.api.services.calendar.model.EventDateTime().setDateTime(com.google.api.client.util.DateTime("2026-11-01T09:00:00+09:00")))
            .setEnd(com.google.api.services.calendar.model.EventDateTime().setDateTime(com.google.api.client.util.DateTime("2026-11-01T10:00:00+09:00")))
        whenever(google.pullEvents(eq(user.id), any(), any(), eq("couple-calendar"))).thenReturn(listOf(remote))
        sync.syncForUser(user.id)
        assertThat(row.title).isEqualTo("사용자 수정")
        assertThat(row.startDateTime.hour).isEqualTo(11)
        assertThat(row.syncStatus).isEqualTo(SyncStatus.PENDING)
    }

}
