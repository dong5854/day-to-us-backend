package com.dong.daytous.controller

import com.dong.daytous.config.SecurityConfig
import com.dong.daytous.config.jwt.JwtAuthenticationFilter
import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.config.jwt.OAuth2AuthenticationSuccessHandler
import com.dong.daytous.dto.PaymentMethodRequest
import com.dong.daytous.dto.PaymentMethodResponse
import com.dong.daytous.service.CustomOAuth2UserService
import com.dong.daytous.service.PaymentMethodService
import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(PaymentMethodController::class)
@Import(SecurityConfig::class)
class PaymentMethodControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @MockitoBean
    lateinit var paymentMethodService: PaymentMethodService

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

    private val spaceId = UUID.randomUUID()
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
    fun `GET 결제 수단 목록을 조회할 수 있다`() {
        val responses = listOf(
            PaymentMethodResponse(id = UUID.randomUUID(), name = "신용카드"),
            PaymentMethodResponse(id = UUID.randomUUID(), name = "현금"),
        )

        whenever(paymentMethodService.getAllPaymentMethods(eq(spaceId), eq(email)))
            .thenReturn(responses)

        mockMvc.perform(
            get("/shared-spaces/$spaceId/payment-methods")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("신용카드"))
            .andExpect(jsonPath("$[1].name").value("현금"))
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `POST 결제 수단을 생성할 수 있다`() {
        val methodId = UUID.randomUUID()
        val request = PaymentMethodRequest(name = "신용카드")
        val response = PaymentMethodResponse(id = methodId, name = "신용카드")

        whenever(paymentMethodService.createPaymentMethod(eq(spaceId), any(), eq(email)))
            .thenReturn(response)

        mockMvc.perform(
            post("/shared-spaces/$spaceId/payment-methods")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("신용카드"))
            .andExpect(jsonPath("$.id").value(methodId.toString()))
    }

    @Test
    fun `DELETE 결제 수단을 삭제할 수 있다`() {
        val methodId = UUID.randomUUID()

        mockMvc.perform(
            delete("/shared-spaces/$spaceId/payment-methods/$methodId")
                .with(user(email))
                .with(csrf()),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `존재하지 않는 결제 수단 삭제 시 404를 반환한다`() {
        val methodId = UUID.randomUUID()

        whenever(paymentMethodService.deletePaymentMethod(eq(spaceId), eq(methodId), eq(email)))
            .thenThrow(EntityNotFoundException("PaymentMethod not found"))

        mockMvc.perform(
            delete("/shared-spaces/$spaceId/payment-methods/$methodId")
                .with(user(email))
                .with(csrf()),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("PaymentMethod not found"))
    }

    @Test
    fun `접근 권한이 없는 공간 조회 시 400을 반환한다`() {
        whenever(paymentMethodService.getAllPaymentMethods(eq(spaceId), eq(email)))
            .thenThrow(IllegalArgumentException("Access denied: User does not belong to this shared space"))

        mockMvc.perform(
            get("/shared-spaces/$spaceId/payment-methods")
                .with(user(email)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Access denied: User does not belong to this shared space"))
    }
}
