package com.dong.daytous.service

import com.dong.daytous.domain.user.*
import com.dong.daytous.dto.*
import com.dong.daytous.repository.*
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class TokenRateLimit : ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS) {
    override fun getHeaders() = HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, "60") }
}

@Service
class PersonalTokenService(private val tokens: PersonalTokenRepository, private val users: UserRepository) {
    private val random = SecureRandom()
    private fun digest(raw: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()))

    @Transactional
    fun issue(email: String, space: UUID, request: TokenRequest): IssuedToken {
        val name = request.name.trim()
        require(name.length in 1..80 && request.days in setOf(7, 30, 90)) { "이름과 만료 기간을 확인해 주세요." }
        val owner = users.findByEmailForUpdate(email).orElseThrow()
        if (owner.sharedSpace?.id != space) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        val now = Instant.now()
        if (tokens.countByOwnerIdAndCreatedAtAfter(owner.id, now.minusSeconds(60)) >= 5) throw TokenRateLimit()
        val secret = "dtu_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val token = PersonalToken(digest = digest(secret), owner = owner, spaceId = space, name = name,
            access = request.access, createdAt = now, expiresAt = now.plus(request.days.toLong(), ChronoUnit.DAYS))
        return IssuedToken(tokens.save(token).info(), secret)
    }

    @Transactional(readOnly = true)
    fun list(email: String, space: UUID) =
        tokens.findByOwnerEmailAndSpaceIdAndRevokedAtIsNullOrderByCreatedAtDesc(email, space).map { it.info() }

    @Transactional
    fun revoke(email: String, space: UUID, id: UUID) {
        tokens.revoke(email, space, id, Instant.now())
    }

    @Transactional
    fun authorize(raw: String, space: UUID, required: TokenAccess): String {
        val token = tokens.findByDigest(digest(raw)) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val now = Instant.now()
        if (token.revokedAt != null || !token.expiresAt.isAfter(now) || token.owner.sharedSpace?.id != token.spaceId)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        if (token.spaceId != space || (required == TokenAccess.EDIT && token.access != required))
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (!token.windowStart.plusSeconds(60).isAfter(now)) {
            token.windowStart = now
            token.requestCount = 0
        }
        if (token.requestCount >= 120) throw TokenRateLimit()
        token.requestCount++
        token.lastUsedAt = now
        return token.owner.email
    }
}
