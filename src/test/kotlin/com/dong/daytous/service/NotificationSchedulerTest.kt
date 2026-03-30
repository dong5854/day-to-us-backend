package com.dong.daytous.service

import com.dong.daytous.domain.fixedexpense.FixedExpense
import com.dong.daytous.domain.fixedexpense.Frequency
import com.dong.daytous.domain.push.PushSubscription
import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.repository.FixedExpenseRepository
import com.dong.daytous.repository.PushSubscriptionRepository
import com.dong.daytous.repository.ScheduleRepository
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NotificationSchedulerTest {

    @Mock
    lateinit var fixedExpenseRepository: FixedExpenseRepository

    @Mock
    lateinit var scheduleRepository: ScheduleRepository

    @Mock
    lateinit var pushSubscriptionRepository: PushSubscriptionRepository

    @Mock
    lateinit var webPushService: WebPushService

    @InjectMocks
    lateinit var notificationScheduler: NotificationScheduler

    private lateinit var sharedSpace: SharedSpace
    private lateinit var user: User
    private lateinit var subscription: PushSubscription
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
        subscription = PushSubscription(
            user = user,
            endpoint = "https://fcm.googleapis.com/fcm/send/test",
            p256dh = "test-p256dh",
            auth = "test-auth",
        ).apply { id = UUID.randomUUID() }
    }

    @Nested
    inner class CalculateNextPaymentDate {

        @Test
        fun `월간 고정지출의 다음 결제일을 계산한다`() {
            val expense = FixedExpense(
                description = "월세",
                amount = BigDecimal("500000"),
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2024, 1, 15),
                sharedSpace = sharedSpace,
            )
            val today = LocalDate.of(2026, 3, 15)

            val result = NotificationScheduler.calculateNextPaymentDate(expense, today)

            assertThat(result).isEqualTo(LocalDate.of(2026, 3, 15))
        }

        @Test
        fun `주간 고정지출의 다음 결제일을 계산한다`() {
            val expense = FixedExpense(
                description = "장보기",
                amount = BigDecimal("100000"),
                frequency = Frequency.WEEKLY,
                startDate = LocalDate.of(2026, 3, 1),
                sharedSpace = sharedSpace,
            )
            val today = LocalDate.of(2026, 3, 15)

            val result = NotificationScheduler.calculateNextPaymentDate(expense, today)

            assertThat(result).isEqualTo(LocalDate.of(2026, 3, 15))
        }

        @Test
        fun `연간 고정지출의 다음 결제일을 계산한다`() {
            val expense = FixedExpense(
                description = "보험료",
                amount = BigDecimal("1200000"),
                frequency = Frequency.YEARLY,
                startDate = LocalDate.of(2024, 6, 1),
                sharedSpace = sharedSpace,
            )
            val today = LocalDate.of(2026, 3, 15)

            val result = NotificationScheduler.calculateNextPaymentDate(expense, today)

            assertThat(result).isEqualTo(LocalDate.of(2026, 6, 1))
        }

        @Test
        fun `시작일이 오늘 이후면 시작일을 반환한다`() {
            val expense = FixedExpense(
                description = "새 구독",
                amount = BigDecimal("10000"),
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2026, 4, 1),
                sharedSpace = sharedSpace,
            )
            val today = LocalDate.of(2026, 3, 15)

            val result = NotificationScheduler.calculateNextPaymentDate(expense, today)

            assertThat(result).isEqualTo(LocalDate.of(2026, 4, 1))
        }

        @Test
        fun `시작일이 오늘이면 오늘을 반환한다`() {
            val today = LocalDate.of(2026, 3, 15)
            val expense = FixedExpense(
                description = "오늘 시작",
                amount = BigDecimal("10000"),
                frequency = Frequency.MONTHLY,
                startDate = today,
                sharedSpace = sharedSpace,
            )

            val result = NotificationScheduler.calculateNextPaymentDate(expense, today)

            assertThat(result).isEqualTo(today)
        }
    }

    @Nested
    inner class SendDailyNotifications {

        @Test
        fun `웹푸시가 비활성화되면 알림을 보내지 않는다`() {
            whenever(webPushService.isEnabled()).thenReturn(false)

            notificationScheduler.sendDailyNotifications()

            verify(fixedExpenseRepository, never()).findAll()
            verify(scheduleRepository, never()).findByStartDateTimeBetween(any(), any())
        }

        @Test
        fun `고정지출 결제일 당일 알림을 발송한다`() {
            whenever(webPushService.isEnabled()).thenReturn(true)

            val today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
            val expense = FixedExpense(
                description = "넷플릭스",
                amount = BigDecimal("17000"),
                frequency = Frequency.MONTHLY,
                startDate = today,
                sharedSpace = sharedSpace,
            ).apply { id = UUID.randomUUID() }

            whenever(fixedExpenseRepository.findAllWithSharedSpace()).thenReturn(listOf(expense))
            whenever(pushSubscriptionRepository.findByUserSharedSpaceIdInWithUser(any())).thenReturn(listOf(subscription))
            whenever(scheduleRepository.findByStartDateTimeBetween(any(), any())).thenReturn(emptyList())

            notificationScheduler.sendDailyNotifications()

            verify(webPushService).sendNotification(
                eq(subscription),
                eq("\uD83D\uDCB3 고정지출 결제일"),
                eq("넷플릭스 ₩17000 결제일입니다"),
                eq("/"),
                any(),
            )
        }

        @Test
        fun `일정 당일 알림을 발송한다`() {
            whenever(webPushService.isEnabled()).thenReturn(true)

            val today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
            val schedule = Schedule(
                title = "데이트",
                startDateTime = LocalDateTime.of(today, java.time.LocalTime.of(14, 0)),
                endDateTime = LocalDateTime.of(today, java.time.LocalTime.of(16, 0)),
                createdBy = 1L,
                sharedSpace = sharedSpace,
            ).apply { id = UUID.randomUUID() }

            whenever(fixedExpenseRepository.findAllWithSharedSpace()).thenReturn(emptyList())
            whenever(scheduleRepository.findByStartDateTimeBetween(any(), any()))
                .thenReturn(listOf(schedule))
                .thenReturn(emptyList())
            whenever(pushSubscriptionRepository.findByUserSharedSpaceId(spaceId)).thenReturn(listOf(subscription))

            notificationScheduler.sendDailyNotifications()

            verify(webPushService).sendNotification(
                eq(subscription),
                eq("\uD83D\uDCC5 오늘 일정"),
                eq("데이트 (14:00)"),
                eq("/"),
                any(),
            )
        }

        @Test
        fun `구독자가 없으면 알림을 보내지 않는다`() {
            whenever(webPushService.isEnabled()).thenReturn(true)
            whenever(fixedExpenseRepository.findAllWithSharedSpace()).thenReturn(emptyList())
            whenever(scheduleRepository.findByStartDateTimeBetween(any(), any())).thenReturn(emptyList())

            notificationScheduler.sendDailyNotifications()

            verify(webPushService, never()).sendNotification(any(), any(), any(), any(), any())
        }
    }
}
