package com.dong.daytous.controller

import com.dong.daytous.config.SecurityConfig
import com.dong.daytous.config.jwt.JwtAuthenticationFilter
import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.config.jwt.OAuth2AuthenticationSuccessHandler
import com.dong.daytous.service.CustomOAuth2UserService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(BudgetController::class)
@Import(SecurityConfig::class)
class SecurityIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var budgetService: com.dong.daytous.service.BudgetService

    @MockitoBean
    lateinit var customOAuth2UserService: CustomOAuth2UserService

    @MockitoBean
    lateinit var oAuth2AuthenticationSuccessHandler: OAuth2AuthenticationSuccessHandler

    @MockitoBean
    lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @MockitoBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    @MockitoBean
    lateinit var clientRegistrationRepository: ClientRegistrationRepository

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
    fun `인증 없이 보호된 엔드포인트 접근 시 401을 반환한다`() {
        mockMvc.perform(get("/shared-spaces"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `인증 없이 보호된 하위 엔드포인트 접근 시 401을 반환한다`() {
        val spaceId = UUID.randomUUID()
        mockMvc.perform(get("/shared-spaces/$spaceId/budget-entries"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `공개 엔드포인트 루트는 인증 없이 접근 가능하다`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `공개 엔드포인트 api-docs는 인증 없이 접근 가능하다`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isNotFound)
    }
}
