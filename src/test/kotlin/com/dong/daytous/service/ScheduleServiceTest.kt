package com.dong.daytous.service

import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.ScheduleRequest
import com.dong.daytous.repository.ScheduleRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ScheduleServiceTest {

    @Mock
    lateinit var scheduleRepository: ScheduleRepository

    @Mock
    lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var googleCalendarService: GoogleCalendarService

    @Mock
    lateinit var syncSettingRepository: com.dong.daytous.repository.SyncSettingRepository

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
    inner class CreateSchedule {

        @Test
        fun `일정을 생성할 수 있다`() {
            val request = ScheduleRequest(
                title = "데이트",
                description = "강남에서 저녁",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                isAllDay = false,
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer {
                (it.arguments[0] as Schedule).apply { id = UUID.randomUUID() }
            }

            val result = scheduleService.createSchedule(spaceId, request, email)

            assertThat(result.title).isEqualTo("데이트")
            assertThat(result.description).isEqualTo("강남에서 저녁")
            assertThat(result.createdBy).isEqualTo(1L)
        }

        @Test
        fun `하루 종일 일정을 생성할 수 있다`() {
            val request = ScheduleRequest(
                title = "여행",
                startDateTime = LocalDateTime.of(2024, 3, 15, 0, 0),
                endDateTime = LocalDateTime.of(2024, 3, 17, 23, 59),
                isAllDay = true,
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer {
                (it.arguments[0] as Schedule).apply { id = UUID.randomUUID() }
            }

            val result = scheduleService.createSchedule(spaceId, request, email)

            assertThat(result.isAllDay).isTrue()
        }

        @Test
        fun `다른 공간에 일정을 생성하면 예외가 발생한다`() {
            val otherSpaceId = UUID.randomUUID()
            val request = ScheduleRequest(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
            )
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                scheduleService.createSchedule(otherSpaceId, request, email)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Access denied")
        }
    }

    @Nested
    inner class GetSchedules {

        @Test
        fun `월별 일정을 조회할 수 있다`() {
            val startOfMonth = LocalDateTime.of(LocalDate.of(2024, 3, 1), LocalTime.MIN)
            val endOfMonth = LocalDateTime.of(YearMonth.of(2024, 3).atEndOfMonth(), LocalTime.MAX)

            val schedule = Schedule(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply { id = UUID.randomUUID() }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(
                scheduleRepository.findBySharedSpaceIdAndStartDateTimeBetweenOrderByStartDateTime(
                    spaceId,
                    startOfMonth,
                    endOfMonth,
                ),
            ).thenReturn(listOf(schedule))

            val result = scheduleService.getSchedules(spaceId, 2024, 3, email)

            assertThat(result).hasSize(1)
            assertThat(result[0].title).isEqualTo("데이트")
        }
    }

    @Nested
    inner class GetScheduleById {

        @Test
        fun `ID로 일정을 조회할 수 있다`() {
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply { id = scheduleId }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule))

            val result = scheduleService.getScheduleById(spaceId, scheduleId, email)

            assertThat(result.title).isEqualTo("데이트")
            assertThat(result.id).isEqualTo(scheduleId)
        }

        @Test
        fun `존재하지 않는 일정을 조회하면 예외가 발생한다`() {
            val scheduleId = UUID.randomUUID()
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                scheduleService.getScheduleById(spaceId, scheduleId, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }

        @Test
        fun `다른 공간의 일정을 조회하면 예외가 발생한다`() {
            val scheduleId = UUID.randomUUID()
            val otherSpace = SharedSpace(name = "Other").apply { id = UUID.randomUUID() }
            val schedule = Schedule(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                createdBy = 1L,
                sharedSpace = otherSpace,
            ).apply { id = scheduleId }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule))

            assertThatThrownBy {
                scheduleService.getScheduleById(spaceId, scheduleId, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class UpdateSchedule {

        @Test
        fun `일정을 수정할 수 있다`() {
            val scheduleId = UUID.randomUUID()
            val existing = Schedule(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply { id = scheduleId }

            val request = ScheduleRequest(
                title = "수정된 데이트",
                description = "홍대에서 저녁",
                startDateTime = LocalDateTime.of(2024, 3, 16, 19, 0),
                endDateTime = LocalDateTime.of(2024, 3, 16, 22, 0),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing))
            whenever(scheduleRepository.save(any<Schedule>())).thenAnswer {
                (it.arguments[0] as Schedule)
            }

            val result = scheduleService.updateSchedule(spaceId, scheduleId, request, email)

            assertThat(result.title).isEqualTo("수정된 데이트")
            assertThat(result.description).isEqualTo("홍대에서 저녁")
            assertThat(result.id).isEqualTo(scheduleId)
            assertThat(result.createdBy).isEqualTo(1L) // 생성자 유지 확인
        }
    }

    @Nested
    inner class DeleteSchedule {

        @Test
        fun `일정을 삭제할 수 있다`() {
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(
                title = "데이트",
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply { id = scheduleId }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule))

            scheduleService.deleteSchedule(spaceId, scheduleId, email)

            verify(scheduleRepository).delete(schedule)
        }

        @Test
        fun `존재하지 않는 일정을 삭제하면 예외가 발생한다`() {
            val scheduleId = UUID.randomUUID()
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                scheduleService.deleteSchedule(spaceId, scheduleId, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }
}
