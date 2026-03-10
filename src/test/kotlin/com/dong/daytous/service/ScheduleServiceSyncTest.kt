package com.dong.daytous.service

import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.schedule.SyncStatus
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.SyncDirection
import com.dong.daytous.domain.user.SyncSetting
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.ScheduleRequest
import com.dong.daytous.repository.ScheduleRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.SyncSettingRepository
import com.dong.daytous.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ScheduleServiceSyncTest {

    @Mock
    lateinit var scheduleRepository: ScheduleRepository

    @Mock
    lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var googleCalendarService: GoogleCalendarService

    @Mock
    lateinit var syncSettingRepository: SyncSettingRepository

    @InjectMocks
    lateinit var scheduleService: ScheduleService

    private lateinit var sharedSpace: SharedSpace
    private lateinit var user: User
    private val spaceId = UUID.randomUUID()
    private val email = "test@example.com"

    @BeforeEach
    fun setUp() {
        sharedSpace = SharedSpace(name = "Test Space").apply { id = spaceId }
        user = User(
            id = 1L,
            name = "Test User",
            email = email,
            role = Role.USER,
            provider = "google",
            providerId = "123",
            sharedSpace = sharedSpace,
        )
    }

    @Nested
    inner class PushSyncSetting {

        @Test
        fun `동기화가 비활성화되면 Google에 push하지 않는다`() {
            val setting = SyncSetting(
                user = user,
                syncEnabled = false,
                syncDirection = SyncDirection.BIDIRECTIONAL,
            )
            val request = ScheduleRequest(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer {
                (it.arguments[0] as Schedule).apply { id = UUID.randomUUID() }
            }
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting))

            scheduleService.createSchedule(spaceId, request, email)

            verify(googleCalendarService, never()).pushEvent(any(), any(), any())
        }

        @Test
        fun `방향이 GOOGLE_TO_APP이면 push하지 않는다`() {
            val setting = SyncSetting(
                user = user,
                syncEnabled = true,
                syncDirection = SyncDirection.GOOGLE_TO_APP,
            )
            val request = ScheduleRequest(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer {
                (it.arguments[0] as Schedule).apply { id = UUID.randomUUID() }
            }
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting))

            scheduleService.createSchedule(spaceId, request, email)

            verify(googleCalendarService, never()).pushEvent(any(), any(), any())
        }

        @Test
        fun `방향이 APP_TO_GOOGLE이면 push한다`() {
            val setting = SyncSetting(
                user = user,
                syncEnabled = true,
                syncDirection = SyncDirection.APP_TO_GOOGLE,
                googleCalendarId = "work",
            )
            val request = ScheduleRequest(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer {
                (it.arguments[0] as Schedule).apply { id = UUID.randomUUID() }
            }
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting))
            whenever(googleCalendarService.pushEvent(eq(1L), any(), eq("work"))).thenReturn("g-id")

            scheduleService.createSchedule(spaceId, request, email)

            verify(googleCalendarService).pushEvent(eq(1L), any(), eq("work"))
        }

        @Test
        fun `Google push 실패 시 PENDING 상태로 마킹한다`() {
            val request = ScheduleRequest(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.empty())
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer {
                (it.arguments[0] as Schedule).apply { id = UUID.randomUUID() }
            }
            whenever(googleCalendarService.pushEvent(eq(1L), any(), any()))
                .thenThrow(RuntimeException("API error"))

            val result = scheduleService.createSchedule(spaceId, request, email)

            assertThat(result.syncStatus).isEqualTo("PENDING")
        }
    }

    @Nested
    inner class ConflictResolution {

        @Test
        fun `CONFLICT 상태가 아닌 일정에 resolve를 호출하면 예외가 발생한다`() {
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply {
                id = scheduleId
                syncStatus = SyncStatus.SYNCED
            }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule))

            assertThatThrownBy {
                scheduleService.resolveConflict(
                    spaceId, scheduleId,
                    com.dong.daytous.service.ConflictResolution.KEEP_LOCAL, email,
                )
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("not in CONFLICT state")
        }

        @Test
        fun `KEEP_LOCAL로 충돌을 해결하면 Google에 push한다`() {
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(
                title = "Local Version",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply {
                id = scheduleId
                googleEventId = "g-conflict"
                syncStatus = SyncStatus.CONFLICT
            }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule))
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.empty())
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer { it.arguments[0] }

            scheduleService.resolveConflict(
                spaceId, scheduleId,
                com.dong.daytous.service.ConflictResolution.KEEP_LOCAL, email,
            )

            verify(googleCalendarService).updateEvent(eq(1L), eq("g-conflict"), any(), any())
        }

        @Test
        fun `충돌이 있는 일정 목록을 조회할 수 있다`() {
            val schedule = Schedule(
                title = "Conflict Schedule",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply {
                id = UUID.randomUUID()
                syncStatus = SyncStatus.CONFLICT
            }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(scheduleRepository.findBySharedSpaceIdAndSyncStatus(spaceId, SyncStatus.CONFLICT))
                .thenReturn(listOf(schedule))

            val result = scheduleService.getConflicts(spaceId, email)

            assertThat(result).hasSize(1)
            assertThat(result[0].syncStatus).isEqualTo("CONFLICT")
        }
    }
}
