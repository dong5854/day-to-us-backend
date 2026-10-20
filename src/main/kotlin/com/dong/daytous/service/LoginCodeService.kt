package com.dong.daytous.service

import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.domain.user.LoginCode
import com.dong.daytous.repository.LoginCodeRepository
import com.dong.daytous.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@Service
class LoginCodeService(
    private val codes: LoginCodeRepository,
    private val users: UserRepository,
    private val tokens: JwtTokenProvider,
) {
    private val random = SecureRandom()
    private fun secret() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    @Transactional
    fun issue(userId: Long): Pair<String, String> {
        val now = Instant.now()
        codes.deleteExpired(now)
        val code = secret()
        val binding = secret()
        codes.save(LoginCode(hash(code), hash(binding), userId, now.plusSeconds(TTL_SECONDS)))
        return code to binding
    }

    @Transactional
    fun exchange(code: String, binding: String): String {
        require(code.matches(SECRET_PATTERN) && binding.matches(SECRET_PATTERN)) { INVALID_CODE }
        val codeHash = hash(code)
        val grant = codes.findById(codeHash).orElseThrow { IllegalArgumentException(INVALID_CODE) }
        // A conditional delete makes simultaneous exchanges single-use across server replicas.
        require(codes.consume(codeHash, hash(binding), Instant.now()) == 1) { INVALID_CODE }
        val user = users.findById(grant.userId).orElseThrow { IllegalArgumentException(INVALID_CODE) }
        return tokens.createToken(user)
    }

    companion object {
        const val COOKIE_NAME = "daytous_login_binding"
        const val COOKIE_PATH = "/auth/login"
        const val TTL_SECONDS = 60L
        private val SECRET_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        private const val INVALID_CODE = "로그인 요청이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요."
    }
}
