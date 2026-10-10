package com.dong.daytous.service

import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SharedSpaceService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SharedSpaceConcurrencyTest {
    @Autowired lateinit var service: SharedSpaceService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var spaces: SharedSpaceRepository
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `같은 사용자의 동시 생성은 공간 하나만 만든다`() {
        val email = newUser()
        val before = spaces.count()

        val failure = overlap(
            first = { service.createSharedSpace("첫 공간", email) },
            second = { service.createSharedSpace("둘째 공간", email) },
        )

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("already belongs")
        assertThat(spaces.count()).isEqualTo(before + 1)
        assertThat(users.findByEmail(email).get().sharedSpace).isNotNull()
    }

    @Test
    fun `다른 사용자의 동시 참여는 정원 두 명을 넘지 않는다`() {
        val space = spaces.saveAndFlush(SharedSpace(name = "정원 테스트"))
        newUser(space)
        val first = newUser()
        val second = newUser()

        val failure = overlap(
            first = { service.joinSharedSpace(space.inviteCode, first) },
            second = { service.joinSharedSpace(space.inviteCode, second) },
        )

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("full")
        assertThat(users.countBySharedSpaceId(space.id!!)).isEqualTo(2)
        assertThat(users.findByEmail(second).get().sharedSpace).isNull()
    }

    @Test
    fun `같은 사용자는 서로 다른 공간에 동시에 참여할 수 없다`() {
        val firstSpace = spaces.saveAndFlush(SharedSpace(name = "공간 A"))
        val secondSpace = spaces.saveAndFlush(SharedSpace(name = "공간 B"))
        val email = newUser()

        val failure = overlap(
            first = { service.joinSharedSpace(firstSpace.inviteCode, email) },
            second = { service.joinSharedSpace(secondSpace.inviteCode, email) },
        )

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("already belongs")
        assertThat(users.findByEmail(email).get().sharedSpace?.id).isEqualTo(firstSpace.id)
        assertThat(users.countBySharedSpaceId(secondSpace.id!!)).isZero()
    }

    private fun newUser(space: SharedSpace? = null): String {
        val key = UUID.randomUUID().toString()
        val email = "$key@test.com"
        users.saveAndFlush(User(
            name = "Test User", email = email, role = Role.USER,
            provider = "google", providerId = key, sharedSpace = space,
        ))
        return email
    }

    // Observe an actual database lock wait, rather than relying on simultaneous thread starts.
    private fun overlap(first: () -> Any, second: () -> Any): Throwable? {
        val ready = CountDownLatch(1)
        val commit = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val waitingQuery = jdbc.dataSource!!.connection.use { connection ->
            when (connection.metaData.databaseProductName) {
                "PostgreSQL" -> "select count(*) from pg_stat_activity where datname = current_database() " +
                    "and cardinality(pg_blocking_pids(pid)) > 0"
                "H2" -> "select count(*) from information_schema.sessions where blocker_id is not null"
                else -> error("Unsupported database for lock observation")
            }
        }
        try {
            val firstResult = executor.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    first()
                    ready.countDown()
                    check(commit.await(10, TimeUnit.SECONDS))
                }
            }
            check(ready.await(5, TimeUnit.SECONDS))
            val secondResult = executor.submit<Throwable?> {
                runCatching { second() }.exceptionOrNull()
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var blocked = false
            while (System.nanoTime() < deadline) {
                blocked = jdbc.queryForObject(
                    waitingQuery,
                    Int::class.java,
                )!! > 0
                if (blocked) break
                Thread.sleep(10)
            }
            check(blocked) { "Second transaction did not wait on a database lock" }
            commit.countDown()
            firstResult.get(5, TimeUnit.SECONDS)
            return secondResult.get(5, TimeUnit.SECONDS)
        } finally {
            commit.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
