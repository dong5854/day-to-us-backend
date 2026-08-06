package com.dong.daytous.controller

import com.dong.daytous.config.SecurityConfig
import com.dong.daytous.config.jwt.JwtAuthenticationFilter
import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.config.jwt.OAuth2AuthenticationSuccessHandler
import com.dong.daytous.dto.ExpenseCategoryRequest
import com.dong.daytous.dto.ExpenseCategoryResponse
import com.dong.daytous.service.CustomOAuth2UserService
import com.dong.daytous.service.ExpenseCategoryService
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

@WebMvcTest(ExpenseCategoryController::class)
@Import(SecurityConfig::class)
class ExpenseCategoryControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @MockitoBean
    lateinit var expenseCategoryService: ExpenseCategoryService

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
    fun `GET 카테고리 목록을 조회할 수 있다`() {
        val responses = listOf(
            ExpenseCategoryResponse(id = UUID.randomUUID(), name = "식비"),
            ExpenseCategoryResponse(id = UUID.randomUUID(), name = "교통비"),
        )

        whenever(expenseCategoryService.getAllCategories(eq(spaceId), eq(email)))
            .thenReturn(responses)

        mockMvc.perform(
            get("/shared-spaces/$spaceId/expense-categories")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("식비"))
            .andExpect(jsonPath("$[1].name").value("교통비"))
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `POST 카테고리를 생성할 수 있다`() {
        val categoryId = UUID.randomUUID()
        val request = ExpenseCategoryRequest(name = "식비")
        val response = ExpenseCategoryResponse(id = categoryId, name = "식비")

        whenever(expenseCategoryService.createCategory(eq(spaceId), any(), eq(email)))
            .thenReturn(response)

        mockMvc.perform(
            post("/shared-spaces/$spaceId/expense-categories")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("식비"))
            .andExpect(jsonPath("$.id").value(categoryId.toString()))
    }

    @Test
    fun `DELETE 카테고리를 삭제할 수 있다`() {
        val categoryId = UUID.randomUUID()

        mockMvc.perform(
            delete("/shared-spaces/$spaceId/expense-categories/$categoryId")
                .with(user(email))
                .with(csrf()),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `존재하지 않는 카테고리 삭제 시 404를 반환한다`() {
        val categoryId = UUID.randomUUID()

        whenever(expenseCategoryService.deleteCategory(eq(spaceId), eq(categoryId), eq(email)))
            .thenThrow(EntityNotFoundException("ExpenseCategory not found"))

        mockMvc.perform(
            delete("/shared-spaces/$spaceId/expense-categories/$categoryId")
                .with(user(email))
                .with(csrf()),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("ExpenseCategory not found"))
    }

    @Test
    fun `접근 권한이 없는 공간 조회 시 400을 반환한다`() {
        whenever(expenseCategoryService.getAllCategories(eq(spaceId), eq(email)))
            .thenThrow(IllegalArgumentException("Access denied: User does not belong to this shared space"))

        mockMvc.perform(
            get("/shared-spaces/$spaceId/expense-categories")
                .with(user(email)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Access denied: User does not belong to this shared space"))
    }
}
