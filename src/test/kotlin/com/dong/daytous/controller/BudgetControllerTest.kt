package com.dong.daytous.controller

import com.dong.daytous.config.SecurityConfig
import com.dong.daytous.config.jwt.JwtAuthenticationFilter
import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.config.jwt.OAuth2AuthenticationSuccessHandler
import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.service.BudgetService
import com.dong.daytous.service.CustomOAuth2UserService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(BudgetController::class)
@Import(SecurityConfig::class)
class BudgetControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @MockitoBean
    lateinit var budgetService: BudgetService

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
    fun `GET 예산 항목 목록을 조회할 수 있다`() {
        val sharedSpace = SharedSpace(name = "Test").apply { id = spaceId }
        val entries = listOf(
            BudgetEntry(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15), sharedSpace = sharedSpace)
                .apply { id = UUID.randomUUID() },
        )
        whenever(budgetService.getAllBudgetEntriesForSpace(eq(spaceId), eq(null), eq(null), eq(email)))
            .thenReturn(entries)

        mockMvc.perform(
            get("/shared-spaces/$spaceId/budget-entries")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].description").value("커피"))
            .andExpect(jsonPath("$[0].amount").value(5000.0))
    }

    @Test
    fun `GET 연월 필터로 예산 항목을 조회할 수 있다`() {
        val sharedSpace = SharedSpace(name = "Test").apply { id = spaceId }
        val entries = listOf(
            BudgetEntry(description = "점심", amount = 12000.0, date = LocalDate.of(2024, 3, 15), sharedSpace = sharedSpace)
                .apply { id = UUID.randomUUID() },
        )
        whenever(budgetService.getAllBudgetEntriesForSpace(eq(spaceId), eq(2024), eq(3), eq(email)))
            .thenReturn(entries)

        mockMvc.perform(
            get("/shared-spaces/$spaceId/budget-entries")
                .param("year", "2024")
                .param("month", "3")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].description").value("점심"))
    }

    @Test
    fun `GET ID로 예산 항목을 조회할 수 있다`() {
        val entryId = UUID.randomUUID()
        val sharedSpace = SharedSpace(name = "Test").apply { id = spaceId }
        val entry = BudgetEntry(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15), sharedSpace = sharedSpace)
            .apply { id = entryId }

        whenever(budgetService.getBudgetEntryById(eq(spaceId), eq(entryId), eq(email)))
            .thenReturn(entry)

        mockMvc.perform(
            get("/shared-spaces/$spaceId/budget-entries/$entryId")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value("커피"))
            .andExpect(jsonPath("$.id").value(entryId.toString()))
    }

    @Test
    fun `POST 예산 항목을 생성할 수 있다`() {
        val sharedSpace = SharedSpace(name = "Test").apply { id = spaceId }
        val request = BudgetEntryRequest(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15))
        val created = BudgetEntry(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15), sharedSpace = sharedSpace)
            .apply { id = UUID.randomUUID() }

        whenever(budgetService.createBudgetEntry(eq(spaceId), any(), eq(email)))
            .thenReturn(created)

        mockMvc.perform(
            post("/shared-spaces/$spaceId/budget-entries")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.description").value("커피"))
    }

    @Test
    fun `PUT 예산 항목을 수정할 수 있다`() {
        val entryId = UUID.randomUUID()
        val sharedSpace = SharedSpace(name = "Test").apply { id = spaceId }
        val request = BudgetEntryRequest(description = "라떼", amount = 6000.0, date = LocalDate.of(2024, 1, 15))
        val updated = BudgetEntry(description = "라떼", amount = 6000.0, date = LocalDate.of(2024, 1, 15), sharedSpace = sharedSpace)
            .apply { id = entryId }

        whenever(budgetService.updateBudgetEntry(eq(spaceId), eq(entryId), any(), eq(email)))
            .thenReturn(updated)

        mockMvc.perform(
            put("/shared-spaces/$spaceId/budget-entries/$entryId")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value("라떼"))
            .andExpect(jsonPath("$.amount").value(6000.0))
    }

    @Test
    fun `DELETE 예산 항목을 삭제할 수 있다`() {
        val entryId = UUID.randomUUID()

        mockMvc.perform(
            delete("/shared-spaces/$spaceId/budget-entries/$entryId")
                .with(user(email))
                .with(csrf()),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `존재하지 않는 항목 조회 시 404를 반환한다`() {
        val entryId = UUID.randomUUID()
        whenever(budgetService.getBudgetEntryById(eq(spaceId), eq(entryId), eq(email)))
            .thenThrow(EntityNotFoundException("BudgetEntry not found"))

        mockMvc.perform(
            get("/shared-spaces/$spaceId/budget-entries/$entryId")
                .with(user(email)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("BudgetEntry not found"))
    }

    @Test
    fun `접근 권한이 없는 공간 조회 시 400을 반환한다`() {
        whenever(budgetService.getAllBudgetEntriesForSpace(eq(spaceId), eq(null), eq(null), eq(email)))
            .thenThrow(IllegalArgumentException("Access denied: User does not belong to this shared space"))

        mockMvc.perform(
            get("/shared-spaces/$spaceId/budget-entries")
                .with(user(email)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Access denied: User does not belong to this shared space"))
    }
}
