package com.dong.daytous.service

import com.dong.daytous.domain.fixedexpense.FixedExpense
import com.dong.daytous.domain.fixedexpense.Frequency
import com.dong.daytous.domain.push.PushSubscription
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.repository.FixedExpenseRepository
import com.dong.daytous.repository.PushSubscriptionRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationSchedulerQueryCountTest {

    @Autowired lateinit var sharedSpaceRepository: SharedSpaceRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var fixedExpenseRepository: FixedExpenseRepository
    @Autowired lateinit var pushSubscriptionRepository: PushSubscriptionRepository
    @Autowired lateinit var entityManager: EntityManager

    private lateinit var sessionFactory: SessionFactory

    companion object {
        const val SPACE_COUNT = 10
        const val EXPENSES_PER_SPACE = 3
        const val USERS_PER_SPACE = 2
    }

    @BeforeEach
    fun setUp() {
        sessionFactory = entityManager.entityManagerFactory.unwrap(SessionFactory::class.java)
        sessionFactory.statistics.isStatisticsEnabled = true

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
                        startDate = LocalDate.now(),
                        sharedSpace = space,
                    )
                )
            }
        }

        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `Before - 반복문 내 개별 쿼리로 N+1 발생`() {
        sessionFactory.statistics.clear()

        val startTime = System.nanoTime()

        // Before 로직 재현
        val allSpaces = sharedSpaceRepository.findAll()
        for (space in allSpaces) {
            val expenses = fixedExpenseRepository.findBySharedSpaceId(space.id!!)
            val subscriptions = pushSubscriptionRepository.findByUserSharedSpaceId(space.id!!)
            // 실제 데이터 접근 (LAZY 로딩 트리거)
            expenses.forEach { it.description }
            subscriptions.forEach { it.endpoint }
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        val queryCount = sessionFactory.statistics.queryExecutionCount

        println("=== Before ===")
        println("Space 수: $SPACE_COUNT")
        println("실행된 쿼리 수: $queryCount")
        println("실행 시간: ${elapsedMs}ms")
        println("예상: 1 (findAll) + $SPACE_COUNT (expenses) + $SPACE_COUNT (subscriptions) = ${1 + SPACE_COUNT * 2}")

        assertThat(queryCount).isGreaterThanOrEqualTo((1 + SPACE_COUNT * 2).toLong())
    }

    @Test
    fun `After - 전체 조회 + groupBy로 쿼리 최소화`() {
        sessionFactory.statistics.clear()

        val startTime = System.nanoTime()

        // After 로직 재현
        val expensesBySpaceId = fixedExpenseRepository.findAllWithSharedSpace()
            .groupBy { it.sharedSpace.id }
        val spaceIds = expensesBySpaceId.keys.filterNotNull()

        val subscriptionsBySpaceId = pushSubscriptionRepository
            .findByUserSharedSpaceIdInWithUser(spaceIds)
            .groupBy { it.user.sharedSpace?.id }

        // 실제 데이터 접근 (이미 fetch 완료되어 추가 쿼리 없음)
        for ((spaceId, expenses) in expensesBySpaceId) {
            val subscriptions = subscriptionsBySpaceId[spaceId] ?: continue
            expenses.forEach { it.description }
            subscriptions.forEach { it.endpoint }
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        val queryCount = sessionFactory.statistics.queryExecutionCount

        println("=== After ===")
        println("Space 수: $SPACE_COUNT")
        println("실행된 쿼리 수: $queryCount")
        println("실행 시간: ${elapsedMs}ms")
        println("예상: 2 (findAllWithSharedSpace + findByUserSharedSpaceIdInWithUser)")

        assertThat(queryCount).isEqualTo(2L)
    }
}
