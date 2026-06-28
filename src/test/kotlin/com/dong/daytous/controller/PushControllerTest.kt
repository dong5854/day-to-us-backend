package com.dong.daytous.controller

import com.dong.daytous.config.SecurityConfig
import com.dong.daytous.config.jwt.JwtAuthenticationFilter
import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.config.jwt.OAuth2AuthenticationSuccessHandler
import com.dong.daytous.dto.PushKeys
import com.dong.daytous.dto.PushSubscribeRequest
import com.dong.daytous.dto.PushSubscriptionResponse
import com.dong.daytous.dto.PushUnsubscribeRequest
import com.dong.daytous.service.CustomOAuth2UserService
import com.dong.daytous.service.PushSubscriptionService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(PushController::class)
@Import(SecurityConfig::class)
class PushControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @MockitoBean
    lateinit var pushSubscriptionService: PushSubscriptionService

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
    fun `GET vapid-key는 인증 없이 접근할 수 있다`() {
        mockMvc.perform(get("/push/vapid-key"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.publicKey").exists())
    }

    @Test
    fun `POST 구독을 생성하면 201을 반환한다`() {
        val request = PushSubscribeRequest(
            endpoint = "https://fcm.googleapis.com/fcm/send/test",
            keys = PushKeys(p256dh = "test-p256dh", auth = "test-auth"),
        )
        val response = PushSubscriptionResponse(
            id = UUID.randomUUID(),
            endpoint = request.endpoint,
            createdAt = LocalDateTime.now(),
        )

        whenever(pushSubscriptionService.subscribe(eq(email), any())).thenReturn(response)

        mockMvc.perform(
            post("/push/subscriptions")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.endpoint").value(request.endpoint))
    }

    @Test
    fun `DELETE 구독을 해제하면 204를 반환한다`() {
        val request = PushUnsubscribeRequest(
            endpoint = "https://fcm.googleapis.com/fcm/send/test",
        )

        mockMvc.perform(
            delete("/push/subscriptions")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `인증 없이 구독하면 401을 반환한다`() {
        val request = PushSubscribeRequest(
            endpoint = "https://fcm.googleapis.com/fcm/send/test",
            keys = PushKeys(p256dh = "test-p256dh", auth = "test-auth"),
        )

        mockMvc.perform(
            post("/push/subscriptions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isUnauthorized)
    }
}
