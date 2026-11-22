package com.dong.daytous.controller

import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.*
import com.dong.daytous.repository.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.time.Instant
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonalTokenIntegrationTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var spaces: SharedSpaceRepository
    @Autowired lateinit var tokens: PersonalTokenRepository
    @Autowired lateinit var jwt: JwtTokenProvider
    @Autowired lateinit var membership: com.dong.daytous.service.SharedSpaceService
    @Autowired lateinit var json: ObjectMapper

    private fun user(space: SharedSpace): User {
        val email = "${UUID.randomUUID()}@test.local"
        return users.save(User(name = "사용자", email = email, role = Role.USER, provider = "google", providerId = email, sharedSpace = space))
    }

    @Test fun `공간별 토큰 발급과 접근 제한 및 폐기를 검증한다`() {
        val space = spaces.save(SharedSpace("공유 공간"))
        val owner = user(space)
        val login = "Bearer " + jwt.createToken(owner)
        val other = "Bearer " + jwt.createToken(user(space))
        val path = "/shared-spaces/${space.id}"
        val management = "$path/api-tokens"
        fun issue(access: String): Pair<String, String> {
            val response = mvc.perform(post(management).header("Authorization", login)
                .contentType("application/json").content("""{"name":"자동화","access":"$access","days":7}"""))
                .andExpect(status().isCreated).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().response
            val body = json.readTree(response.contentAsByteArray)
            return body["info"]["id"].asString() to ("Bearer " + body["secret"].asString())
        }
        val staleLogin = UsernamePasswordAuthenticationToken.authenticated(owner.email, null, emptyList()).apply {
            details = Instant.now().minusSeconds(601)
        }
        mvc.perform(post(management).with(authentication(staleLogin))
            .contentType("application/json").content("""{"name":"오래된 로그인"}"""))
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("RECENT_LOGIN_REQUIRED"))
        val (id, readToken) = issue("READ")
        mvc.perform(get("$path/schedules").header("Authorization", readToken)).andExpect(status().isOk)
        mvc.perform(get(management).header("Authorization", login)).andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty)
            .andExpect(jsonPath("$[0].secret").doesNotExist())
        mvc.perform(get(management).header("Authorization", other)).andExpect(jsonPath("$").isEmpty)
        mvc.perform(delete("$management/$id").header("Authorization", other)).andExpect(status().isNoContent)
        mvc.perform(post("$path/expense-categories").header("Authorization", readToken)).andExpect(status().isForbidden)
        mvc.perform(get("/shared-spaces/${UUID.randomUUID()}/schedules").header("Authorization", readToken)).andExpect(status().isForbidden)
        val (_, editToken) = issue("EDIT")
        mvc.perform(post("$path/expense-categories").header("Authorization", editToken)
            .contentType("application/json").content("""{"name":"간식"}""")).andExpect(status().isCreated)
        mvc.perform(get("$path/pension-connections").header("Authorization", editToken)).andExpect(status().isForbidden)
        mvc.perform(get("$path/savings?year=2026&month=11&group=pension").header("Authorization", editToken)).andExpect(status().isForbidden)
        val pension = mvc.perform(post("$path/savings").header("Authorization", login)
            .contentType("application/json").content("""{"name":"연금","payerEmail":"${owner.email}","kind":"IRP"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString.trim('"')
        mvc.perform(delete("$path/savings/$pension").header("Authorization", editToken)).andExpect(status().isForbidden)
        mvc.perform(post(management).header("Authorization", editToken)).andExpect(status().isForbidden)
        mvc.perform(get("/shared-spaces/members").header("Authorization", editToken)).andExpect(status().isForbidden)
        mvc.perform(delete("$management/$id").header("Authorization", login)).andExpect(status().isNoContent)
        mvc.perform(get("$path/schedules").header("Authorization", readToken)).andExpect(status().isUnauthorized)
        owner.sharedSpace = null
        users.save(owner)
        mvc.perform(get("$path/schedules").header("Authorization", editToken)).andExpect(status().isUnauthorized)
        membership.joinSharedSpace(space.inviteCode, owner.email)
        mvc.perform(get("$path/schedules").header("Authorization", editToken)).andExpect(status().isUnauthorized)
        assertThat(tokens.findById(UUID.fromString(id)).orElseThrow().digest).hasSize(64)
    }

    @Test fun `만료 및 호출 한도와 발급 한도를 적용한다`() {
        val space = spaces.save(SharedSpace("한도 확인"))
        val owner = user(space)
        val login = "Bearer " + jwt.createToken(owner)
        val path = "/shared-spaces/${space.id}"
        fun issue() = mvc.perform(post("$path/api-tokens").header("Authorization", login)
            .contentType("application/json").content("""{"name":"테스트"}"""))
        val result = json.readTree(issue().andExpect(status().isCreated).andReturn().response.contentAsByteArray)
        val raw = result["secret"].asString()
        val id = UUID.fromString(result["info"]["id"].asString())
        val token = tokens.findById(id).orElseThrow()
        token.requestCount = 120
        tokens.save(token)
        mvc.perform(get("$path/schedules").header("Authorization", "Bearer $raw"))
            .andExpect(status().isTooManyRequests).andExpect(header().string("Retry-After", "60"))
        // Use the digest of a known test secret to exercise the complete filter path.
        val expiredRaw = "dtu_" + "a".repeat(43)
        val hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(expiredRaw.toByteArray()))
        tokens.save(PersonalToken(digest = hash, owner = owner, spaceId = space.id!!, name = "만료",
            access = TokenAccess.READ, createdAt = java.time.Instant.EPOCH, expiresAt = java.time.Instant.EPOCH))
        mvc.perform(get("$path/schedules").header("Authorization", "Bearer $expiredRaw")).andExpect(status().isUnauthorized)
        repeat(4) { issue().andExpect(status().isCreated) }
        issue().andExpect(status().isTooManyRequests).andExpect(header().string("Retry-After", "60"))
    }
}
