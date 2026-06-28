package com.dong.daytous.controller

import com.dong.daytous.config.SecurityConfig
import com.dong.daytous.config.jwt.JwtAuthenticationFilter
import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.config.jwt.OAuth2AuthenticationSuccessHandler
import com.dong.daytous.dto.JoinSharedSpaceRequest
import com.dong.daytous.dto.SharedSpaceRequest
import com.dong.daytous.dto.SharedSpaceResponse
import com.dong.daytous.dto.UserResponse
import com.dong.daytous.service.CustomOAuth2UserService
import com.dong.daytous.service.SharedSpaceService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import org.mockito.kotlin.any
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(SharedSpaceController::class)
@Import(SecurityConfig::class)
class SharedSpaceControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @MockitoBean
    lateinit var sharedSpaceService: SharedSpaceService

    @MockitoBean
    lateinit var clientRegistrationRepository: ClientRegistrationRepository

    @MockitoBean
    lateinit var customOAuth2UserService: CustomOAuth2UserService

    @MockitoBean
    lateinit var oAuth2AuthenticationSuccessHandler: OAuth2AuthenticationSuccessHandler

    @MockitoBean
    lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @MockitoBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    private val email = "test@example.com"

    @BeforeEach
    fun setUp() {
        whenever(jwtAuthenticationFilter.doFilter(any<HttpServletRequest>(), any<HttpServletResponse>(), any<FilterChain>()))
            .thenAnswer { invocation ->
                val chain = invocation.getArgument<FilterChain>(2)
                chain.doFilter(
                    invocation.getArgument<HttpServletRequest>(0),
                    invocation.getArgument<HttpServletResponse>(1),
                )
            }
    }

    @Test
    fun `POST 공유 공간을 생성할 수 있다`() {
        val request = SharedSpaceRequest(name = "우리 공간")
        val response = SharedSpaceResponse(id = UUID.randomUUID(), name = "우리 공간", inviteCode = "abc12345")

        whenever(sharedSpaceService.createSharedSpace(eq("우리 공간"), eq(email))).thenReturn(response)

        mockMvc.perform(
            post("/shared-spaces")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("우리 공간"))
            .andExpect(jsonPath("$.inviteCode").value("abc12345"))
    }

    @Test
    fun `POST 초대 코드로 공유 공간에 참가할 수 있다`() {
        val request = JoinSharedSpaceRequest(inviteCode = "abc12345")
        val response = SharedSpaceResponse(id = UUID.randomUUID(), name = "커플 공간", inviteCode = "abc12345")

        whenever(sharedSpaceService.joinSharedSpace(eq("abc12345"), eq(email))).thenReturn(response)

        mockMvc.perform(
            post("/shared-spaces/join")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("커플 공간"))
    }

    @Test
    fun `POST 가득 찬 공간에 참가하면 500을 반환한다`() {
        val request = JoinSharedSpaceRequest(inviteCode = "abc12345")
        whenever(sharedSpaceService.joinSharedSpace(eq("abc12345"), eq(email)))
            .thenThrow(IllegalStateException("Shared space is full"))

        mockMvc.perform(
            post("/shared-spaces/join")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Shared space is full"))
    }

    @Test
    fun `POST 잘못된 초대 코드로 참가하면 404를 반환한다`() {
        val request = JoinSharedSpaceRequest(inviteCode = "invalid")
        whenever(sharedSpaceService.joinSharedSpace(eq("invalid"), eq(email)))
            .thenThrow(EntityNotFoundException("Invalid invite code"))

        mockMvc.perform(
            post("/shared-spaces/join")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Invalid invite code"))
    }

    @Test
    fun `GET 내 공유 공간을 조회할 수 있다`() {
        val responses = listOf(
            SharedSpaceResponse(id = UUID.randomUUID(), name = "우리 공간", inviteCode = "abc12345"),
        )
        whenever(sharedSpaceService.getMySharedSpaces(eq(email))).thenReturn(responses)

        mockMvc.perform(
            get("/shared-spaces")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("우리 공간"))
    }

    @Test
    fun `GET 멤버 목록을 조회할 수 있다`() {
        val members = listOf(
            UserResponse(name = "User1", email = "u1@test.com"),
            UserResponse(name = "User2", email = "u2@test.com"),
        )
        whenever(sharedSpaceService.getMembers(eq(email))).thenReturn(members)

        mockMvc.perform(
            get("/shared-spaces/members")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("User1"))
            .andExpect(jsonPath("$[1].name").value("User2"))
    }

    @Test
    fun `POST 이미 공간에 속한 사용자가 생성하면 500을 반환한다`() {
        val request = SharedSpaceRequest(name = "새 공간")
        whenever(sharedSpaceService.createSharedSpace(eq("새 공간"), eq(email)))
            .thenThrow(IllegalStateException("User already belongs to a shared space"))

        mockMvc.perform(
            post("/shared-spaces")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("User already belongs to a shared space"))
    }
}
