package com.dong.daytous.service

import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.schedule.SyncStatus
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.GoogleToken
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.SyncDirection
import com.dong.daytous.domain.user.SyncSetting
import com.dong.daytous.domain.user.User
import com.dong.daytous.repository.GoogleTokenRepository
import com.dong.daytous.repository.ScheduleRepository
import com.dong.daytous.repository.SyncSettingRepository
import com.dong.daytous.repository.UserRepository
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GoogleCalendarSyncServiceTest {

    @Mock
    lateinit var googleCalendarService: GoogleCalendarService

    @Mock
    lateinit var scheduleRepository: ScheduleRepository

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var googleTokenRepository: GoogleTokenRepository

    @Mock
    lateinit var syncSettingRepository: SyncSettingRepository

    @InjectMocks
    lateinit var syncService: GoogleCalendarSyncService

    private lateinit var user: User
    private lateinit var sharedSpace: SharedSpace
    private val spaceId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        sharedSpace = SharedSpace(name = "Test Space").apply { id = spaceId }
        user = User(
            id = 1L,
            name = "Test User",
            email = "test@example.com",
            role = Role.USER,
            provider = "google",
            providerId = "123",
            sharedSpace = sharedSpace,
        )
    }

    private fun createGoogleEvent(
        eventId: String,
        summary: String,
        startMillis: Long,
        endMillis: Long,
        updatedMillis: Long? = null,
        status: String = "confirmed",
    ): Event {
        val event = Event()
            .setId(eventId)
            .setSummary(summary)
            .setStatus(status)
        event.start = EventDateTime().setDateTime(DateTime(startMillis))
        event.end = EventDateTime().setDateTime(DateTime(endMillis))
        if (updatedMillis != null) {
            event.updated = DateTime(updatedMillis)
        }
        return event
    }

    private fun toMillis(ldt: LocalDateTime): Long {
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Nested
    inner class SyncForUser {

        @Test
        fun `Google에서 새 이벤트를 가져와 Schedule을 생성한다`() {
            val now = LocalDateTime.now()
            val startMillis = toMillis(now.plusDays(1))
            val endMillis = toMillis(now.plusDays(1).plusHours(2))

            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.empty())
            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(googleCalendarService.pullEvents(eq(1L), any(), any(), eq("primary")))
                .thenReturn(listOf(createGoogleEvent("g1", "Google Event", startMillis, endMillis, toMillis(now))))
            whenever(scheduleRepository.findBySharedSpaceIdAndGoogleEventIdIn(eq(spaceId), any()))
                .thenReturn(emptyList())
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer { it.arguments[0] }

            syncService.syncForUser(1L)

            verify(scheduleRepository).save(argThat<Schedule> {
                title == "Google Event" && googleEventId == "g1" && syncStatus == SyncStatus.SYNCED
            })
        }

        @Test
        fun `Google 이벤트가 cancelled 상태이면 Schedule을 삭제한다`() {
            val schedule = Schedule(
                title = "Existing",
                startDateTime = LocalDateTime.now(),
                endDateTime = LocalDateTime.now().plusHours(1),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply {
                id = UUID.randomUUID()
                googleEventId = "g-cancelled"
                syncStatus = SyncStatus.SYNCED
            }

            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.empty())
            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(googleCalendarService.pullEvents(eq(1L), any(), any(), eq("primary")))
                .thenReturn(listOf(createGoogleEvent("g-cancelled", "", 0, 0, status = "cancelled")))
            whenever(scheduleRepository.findBySharedSpaceIdAndGoogleEventIdIn(eq(spaceId), any()))
                .thenReturn(listOf(schedule))

            syncService.syncForUser(1L)

            verify(scheduleRepository).delete(schedule)
        }

        @Test
        fun `양쪽이 모두 수정되었으면 CONFLICT로 마킹한다`() {
            val lastSynced = LocalDateTime.now().minusHours(2)
            val localModified = LocalDateTime.now().minusMinutes(30)
            val googleModified = LocalDateTime.now().minusMinutes(15)

            val schedule = Schedule(
                title = "Existing",
                startDateTime = LocalDateTime.now(),
                endDateTime = LocalDateTime.now().plusHours(1),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply {
                id = UUID.randomUUID()
                googleEventId = "g-conflict"
                syncStatus = SyncStatus.SYNCED
                lastSyncedAt = lastSynced
                lastModifiedAt = localModified
            }

            val googleEvent = createGoogleEvent(
                "g-conflict", "Updated on Google",
                toMillis(LocalDateTime.now()), toMillis(LocalDateTime.now().plusHours(1)),
                toMillis(googleModified),
            )

            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.empty())
            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(googleCalendarService.pullEvents(eq(1L), any(), any(), eq("primary")))
                .thenReturn(listOf(googleEvent))
            whenever(scheduleRepository.findBySharedSpaceIdAndGoogleEventIdIn(eq(spaceId), any()))
                .thenReturn(listOf(schedule))
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer { it.arguments[0] }

            syncService.syncForUser(1L)

            verify(scheduleRepository).save(argThat<Schedule> {
                syncStatus == SyncStatus.CONFLICT
            })
        }

        @Test
        fun `Google만 수정되었으면 Google 버전으로 업데이트한다`() {
            val lastSynced = LocalDateTime.now().minusHours(2)
            val localModified = lastSynced.minusMinutes(10) // local NOT modified since sync
            val googleModified = LocalDateTime.now().minusMinutes(15)

            val schedule = Schedule(
                title = "Old Title",
                startDateTime = LocalDateTime.now(),
                endDateTime = LocalDateTime.now().plusHours(1),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply {
                id = UUID.randomUUID()
                googleEventId = "g-update"
                syncStatus = SyncStatus.SYNCED
                lastSyncedAt = lastSynced
                lastModifiedAt = localModified
            }

            val googleEvent = createGoogleEvent(
                "g-update", "New Title from Google",
                toMillis(LocalDateTime.now()), toMillis(LocalDateTime.now().plusHours(1)),
                toMillis(googleModified),
            )

            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.empty())
            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(googleCalendarService.pullEvents(eq(1L), any(), any(), eq("primary")))
                .thenReturn(listOf(googleEvent))
            whenever(scheduleRepository.findBySharedSpaceIdAndGoogleEventIdIn(eq(spaceId), any()))
                .thenReturn(listOf(schedule))
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer { it.arguments[0] }

            syncService.syncForUser(1L)

            verify(scheduleRepository).save(argThat<Schedule> {
                title == "New Title from Google" && syncStatus == SyncStatus.SYNCED
            })
        }
    }

    @Nested
    inner class SyncSettingRespect {

        @Test
        fun `동기화가 비활성화되어 있으면 pull을 건너뛴다`() {
            val setting = SyncSetting(
                user = user,
                syncEnabled = false,
                syncDirection = SyncDirection.BIDIRECTIONAL,
            )
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting))

            syncService.syncForUser(1L)

            verify(googleCalendarService, never()).pullEvents(any(), any(), any(), any())
        }

        @Test
        fun `방향이 APP_TO_GOOGLE이면 pull을 건너뛴다`() {
            val setting = SyncSetting(
                user = user,
                syncEnabled = true,
                syncDirection = SyncDirection.APP_TO_GOOGLE,
            )
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting))

            syncService.syncForUser(1L)

            verify(googleCalendarService, never()).pullEvents(any(), any(), any(), any())
        }

        @Test
        fun `설정된 googleCalendarId를 사용한다`() {
            val setting = SyncSetting(
                user = user,
                syncEnabled = true,
                syncDirection = SyncDirection.BIDIRECTIONAL,
                googleCalendarId = "work-calendar",
            )
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting))
            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(googleCalendarService.pullEvents(eq(1L), any(), any(), eq("work-calendar")))
                .thenReturn(emptyList())

            syncService.syncForUser(1L)

            verify(googleCalendarService).pullEvents(eq(1L), any(), any(), eq("work-calendar"))
        }
    }

    @Nested
    inner class RetryPending {

        @Test
        fun `PENDING 상태의 새 일정을 재시도하여 push한다`() {
            val schedule = Schedule(
                title = "Pending Schedule",
                startDateTime = LocalDateTime.now(),
                endDateTime = LocalDateTime.now().plusHours(1),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply {
                id = UUID.randomUUID()
                googleEventId = null
                syncStatus = SyncStatus.PENDING
            }

            val token = GoogleToken(
                user = user,
                accessToken = "enc-access",
                refreshToken = "enc-refresh",
                expiresAt = LocalDateTime.now().plusHours(1),
            )

            whenever(googleTokenRepository.findAll()).thenReturn(listOf(token))
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.empty())
            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(googleCalendarService.pullEvents(eq(1L), any(), any(), eq("primary")))
                .thenReturn(emptyList())
            whenever(scheduleRepository.findBySyncStatus(SyncStatus.PENDING)).thenReturn(listOf(schedule))
            whenever(googleCalendarService.pushEvent(eq(1L), any(), any())).thenReturn("new-google-id")
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer { it.arguments[0] }

            syncService.syncAllUsers()

            verify(googleCalendarService).pushEvent(eq(1L), eq(schedule), any())
            assertThat(schedule.googleEventId).isEqualTo("new-google-id")
            assertThat(schedule.syncStatus).isEqualTo(SyncStatus.SYNCED)
        }
    }
}
