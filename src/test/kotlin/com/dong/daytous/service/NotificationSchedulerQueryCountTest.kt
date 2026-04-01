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
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationSchedulerQueryCountTest {

    @Autowired lateinit var sharedSpaceRepository: SharedSpaceRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var fixedExpenseRepository: FixedExpenseRepository
    @Autowired lateinit var scheduleRepository: ScheduleRepository
    @Autowired lateinit var pushSubscriptionRepository: PushSubscriptionRepository
    @Autowired lateinit var entityManager: EntityManager

    private lateinit var sessionFactory: SessionFactory

    companion object {
        const val SPACE_COUNT = 10
        const val EXPENSES_PER_SPACE = 3
        const val USERS_PER_SPACE = 2
        const val SCHEDULES_PER_SPACE = 3
    }

    @BeforeEach
    fun setUp() {
        sessionFactory = entityManager.entityManagerFactory.unwrap(SessionFactory::class.java)
        sessionFactory.statistics.isStatisticsEnabled = true

        val today = LocalDate.now()

        for (i in 1..SPACE_COUNT) {
            val space = sharedSpaceRepository.save(SharedSpace(name = "Space $i"))

            for (j in 1..USERS_PER_SPACE) {
                val user = userRepository.save(
                    User(
                        name = "User $i-$j",
                        email = "user$i-$j@test.com",
                        role = Role.USER,
                        provider = "google",
                        providerId = "pid-$i-$j",
                        sharedSpace = space,
                    )
                )
                pushSubscriptionRepository.save(
                    PushSubscription(
                        user = user,
                        endpoint = "https://fcm.googleapis.com/test/$i/$j",
                        p256dh = "p256dh-$i-$j",
                        auth = "auth-$i-$j",
                    )
                )
            }

            for (k in 1..EXPENSES_PER_SPACE) {
                fixedExpenseRepository.save(
                    FixedExpense(
                        description = "Expense $i-$k",
                        amount = BigDecimal("10000"),
                        frequency = Frequency.MONTHLY,
                        startDate = today,
                        sharedSpace = space,
                    )
                )
            }

            for (k in 1..SCHEDULES_PER_SPACE) {
                scheduleRepository.save(
                    Schedule(
                        title = "Schedule $i-$k",
                        startDateTime = LocalDateTime.of(today, LocalTime.of(9 + k, 0)),
                        endDateTime = LocalDateTime.of(today, LocalTime.of(10 + k, 0)),
                        createdBy = 1L,
                        sharedSpace = space,
                    )
                )
            }
        }

        entityManager.flush()
        entityManager.clear()
    }

    @Nested
    inner class FixedExpenseNotifications {

        @Test
        fun `Before - 반복문 내 개별 쿼리로 N+1 발생`() {
            sessionFactory.statistics.clear()

            val startTime = System.nanoTime()

            val allSpaces = sharedSpaceRepository.findAll()
            for (space in allSpaces) {
                val expenses = fixedExpenseRepository.findBySharedSpaceId(space.id!!)
                val subscriptions = pushSubscriptionRepository.findByUserSharedSpaceId(space.id!!)
                expenses.forEach { it.description }
                subscriptions.forEach { it.endpoint }
            }

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            val queryCount = sessionFactory.statistics.queryExecutionCount

            println("=== FixedExpense Before ===")
            println("Space 수: $SPACE_COUNT, 실행된 쿼리 수: $queryCount, 실행 시간: ${elapsedMs}ms")

            assertThat(queryCount).isGreaterThanOrEqualTo((1 + SPACE_COUNT * 2).toLong())
        }

        @Test
        fun `After - 전체 조회 + groupBy로 쿼리 최소화`() {
            sessionFactory.statistics.clear()

            val startTime = System.nanoTime()

            val expensesBySpaceId = fixedExpenseRepository.findAllWithSharedSpace()
                .groupBy { it.sharedSpace.id }
            val spaceIds = expensesBySpaceId.keys.filterNotNull()

            val subscriptionsBySpaceId = pushSubscriptionRepository
                .findByUserSharedSpaceIdInWithUser(spaceIds)
                .groupBy { it.user.sharedSpace?.id }

            for ((spaceId, expenses) in expensesBySpaceId) {
                val subscriptions = subscriptionsBySpaceId[spaceId] ?: continue
                expenses.forEach { it.description }
                subscriptions.forEach { it.endpoint }
            }

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            val queryCount = sessionFactory.statistics.queryExecutionCount

            println("=== FixedExpense After ===")
            println("Space 수: $SPACE_COUNT, 실행된 쿼리 수: $queryCount, 실행 시간: ${elapsedMs}ms")

            assertThat(queryCount).isEqualTo(2L)
        }
    }

    @Nested
    inner class ScheduleNotifications {

        @Test
        fun `Before - schedule마다 sharedSpace LAZY 로딩 + subscription 개별 조회`() {
            sessionFactory.statistics.clear()

            val today = LocalDate.now()
            val todayStart = LocalDateTime.of(today, LocalTime.MIN)
            val todayEnd = LocalDateTime.of(today, LocalTime.MAX)

            val startTime = System.nanoTime()

            val schedules = scheduleRepository.findByStartDateTimeBetween(todayStart, todayEnd)
            for (schedule in schedules) {
                val spaceId = schedule.sharedSpace.id!!  // LAZY 로딩
                val subscriptions = pushSubscriptionRepository.findByUserSharedSpaceId(spaceId)
                subscriptions.forEach { it.endpoint }
            }

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            val queryCount = sessionFactory.statistics.queryExecutionCount
            val totalSchedules = SPACE_COUNT * SCHEDULES_PER_SPACE

            println("=== Schedule Before ===")
            println("Schedule 수: $totalSchedules, 실행된 쿼리 수: $queryCount, 실행 시간: ${elapsedMs}ms")

            // 1 (findByStartDateTimeBetween) + N (sharedSpace LAZY) + N (subscriptions)
            assertThat(queryCount).isGreaterThanOrEqualTo((1 + totalSchedules).toLong())
        }

        @Test
        fun `After - JOIN FETCH + 배치 조회로 쿼리 최소화`() {
            sessionFactory.statistics.clear()

            val today = LocalDate.now()
            val todayStart = LocalDateTime.of(today, LocalTime.MIN)
            val todayEnd = LocalDateTime.of(today, LocalTime.MAX)

            val startTime = System.nanoTime()

            val schedules = scheduleRepository.findByStartDateTimeBetweenWithSharedSpace(todayStart, todayEnd)
            if (schedules.isNotEmpty()) {
                val spaceIds = schedules.map { it.sharedSpace.id!! }.distinct()
                val subscriptionsBySpaceId = pushSubscriptionRepository
                    .findByUserSharedSpaceIdInWithUser(spaceIds)
                    .groupBy { it.user.sharedSpace?.id }

                for (schedule in schedules) {
                    val subscriptions = subscriptionsBySpaceId[schedule.sharedSpace.id] ?: continue
                    subscriptions.forEach { it.endpoint }
                }
            }

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            val queryCount = sessionFactory.statistics.queryExecutionCount

            println("=== Schedule After ===")
            println("Schedule 수: ${SPACE_COUNT * SCHEDULES_PER_SPACE}, 실행된 쿼리 수: $queryCount, 실행 시간: ${elapsedMs}ms")

            assertThat(queryCount).isEqualTo(2L)
        }
    }
}
