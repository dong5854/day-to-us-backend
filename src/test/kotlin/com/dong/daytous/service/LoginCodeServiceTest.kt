package com.dong.daytous.service

import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DataJpaTest
@ActiveProfiles("test")
@Import(LoginCodeService::class, JwtTokenProvider::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LoginCodeServiceTest {
    @Autowired lateinit var service: LoginCodeService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var tokens: JwtTokenProvider
    @Autowired lateinit var jdbc: JdbcTemplate

    private fun issue(): Pair<String, String> {
        val id = UUID.randomUUID().toString()
        val user = users.saveAndFlush(User(name = "Login test", email = "$id@test.com",
            role = Role.USER, provider = "google", providerId = id))
        return service.issue(user.id)
    }

    @Test
    fun `코드는 해당 브라우저에서 한 번만 사용 가능하다`() {
        val (code, binding) = issue()
        assertThat(code).hasSize(43)
        assertThatThrownBy { service.exchange(code, "x".repeat(43)) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.exchange(code, "") }.isInstanceOf(IllegalArgumentException::class.java)
        val token = service.exchange(code, binding)
        assertThat(tokens.validateToken(token)).isTrue()
        assertThatThrownBy { service.exchange(code, binding) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `만료된 코드는 교환할 수 없고 새 로그인 시 정리된다`() {
        val (code, binding) = issue()
        jdbc.update("update login_code set expires_at = ?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)))
        assertThatThrownBy { service.exchange(code, binding) }.isInstanceOf(IllegalArgumentException::class.java)
        issue()
        assertThat(jdbc.queryForObject("select count(*) from login_code where expires_at <= CURRENT_TIMESTAMP", Int::class.java)).isZero()
    }

    @Test
    fun `동시에 교환해도 하나의 요청만 성공한다`() {
        val (code, binding) = issue()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map {
                executor.submit<Boolean> {
                    start.await(5, TimeUnit.SECONDS)
                    runCatching { service.exchange(code, binding) }.isSuccess
                }
            }
            start.countDown()
            assertThat(results.count { it.get(10, TimeUnit.SECONDS) }).isEqualTo(1)
        } finally { executor.shutdownNow() }
    }
}
