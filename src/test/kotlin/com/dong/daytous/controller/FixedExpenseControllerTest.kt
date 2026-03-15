package com.dong.daytous.controller

import com.dong.daytous.config.SecurityConfig
import com.dong.daytous.config.jwt.JwtAuthenticationFilter
import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.config.jwt.OAuth2AuthenticationSuccessHandler
import com.dong.daytous.domain.fixedexpense.Frequency
import com.dong.daytous.dto.FixedExpenseRequest
import com.dong.daytous.dto.FixedExpenseResponse
import com.dong.daytous.service.CustomOAuth2UserService
import com.dong.daytous.service.FixedExpenseService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(FixedExpenseController::class)
@Import(SecurityConfig::class)
class FixedExpenseControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @MockitoBean
    lateinit var fixedExpenseService: FixedExpenseService

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
    fun `POST 고정 지출을 생성할 수 있다`() {
        val request = FixedExpenseRequest(
            description = "월세",
            amount = BigDecimal("500000"),
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2024, 1, 1),
        )
        val response = FixedExpenseResponse(
            id = UUID.randomUUID(),
            description = "월세",
            amount = BigDecimal("500000"),
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2024, 1, 1),
        )

        whenever(fixedExpenseService.createFixedExpense(eq(spaceId), any(), eq(email)))
            .thenReturn(response)

        mockMvc.perform(
            post("/shared-spaces/$spaceId/fixed-expenses")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.description").value("월세"))
            .andExpect(jsonPath("$.frequency").value("MONTHLY"))
    }

    @Test
    fun `GET 고정 지출 목록을 조회할 수 있다`() {
        val responses = listOf(
            FixedExpenseResponse(
                id = UUID.randomUUID(),
                description = "월세",
                amount = BigDecimal("500000"),
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2024, 1, 1),
            ),
            FixedExpenseResponse(
                id = UUID.randomUUID(),
                description = "보험",
                amount = BigDecimal("100000"),
                frequency = Frequency.YEARLY,
                startDate = LocalDate.of(2024, 6, 1),
            ),
        )

        whenever(fixedExpenseService.getAllFixedExpenses(eq(spaceId), eq(email)))
            .thenReturn(responses)

        mockMvc.perform(
            get("/shared-spaces/$spaceId/fixed-expenses")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].description").value("월세"))
            .andExpect(jsonPath("$[1].description").value("보험"))
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `PUT 고정 지출을 수정할 수 있다`() {
        val expenseId = UUID.randomUUID()
        val request = FixedExpenseRequest(
            description = "월세 인상",
            amount = BigDecimal("550000"),
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2024, 3, 1),
        )
        val response = FixedExpenseResponse(
            id = expenseId,
            description = "월세 인상",
            amount = BigDecimal("550000"),
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2024, 3, 1),
        )

        whenever(fixedExpenseService.updateFixedExpense(eq(spaceId), eq(expenseId), any(), eq(email)))
            .thenReturn(response)

        mockMvc.perform(
            put("/shared-spaces/$spaceId/fixed-expenses/$expenseId")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value("월세 인상"))
            .andExpect(jsonPath("$.amount").value(550000))
    }

    @Test
    fun `DELETE 고정 지출을 삭제할 수 있다`() {
        val expenseId = UUID.randomUUID()

        mockMvc.perform(
            delete("/shared-spaces/$spaceId/fixed-expenses/$expenseId")
                .with(user(email))
                .with(csrf()),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `접근 권한이 없으면 400을 반환한다`() {
        whenever(fixedExpenseService.getAllFixedExpenses(eq(spaceId), eq(email)))
            .thenThrow(IllegalArgumentException("Access denied: User does not belong to this shared space"))

        mockMvc.perform(
            get("/shared-spaces/$spaceId/fixed-expenses")
                .with(user(email)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Access denied: User does not belong to this shared space"))
    }
}
