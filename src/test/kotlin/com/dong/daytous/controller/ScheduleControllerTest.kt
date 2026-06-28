package com.dong.daytous.controller

import com.dong.daytous.config.SecurityConfig
import com.dong.daytous.config.jwt.JwtAuthenticationFilter
import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.config.jwt.OAuth2AuthenticationSuccessHandler
import com.dong.daytous.dto.ScheduleRequest
import com.dong.daytous.dto.ScheduleResponse
import com.dong.daytous.service.CustomOAuth2UserService
import com.dong.daytous.service.ScheduleService
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
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(ScheduleController::class)
@Import(SecurityConfig::class)
class ScheduleControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @MockitoBean
    lateinit var scheduleService: ScheduleService

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
    fun `POST 일정을 생성할 수 있다`() {
        val request = ScheduleRequest(
            title = "데이트",
            description = "강남 저녁",
            startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
            endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
        )
        val response = ScheduleResponse(
            id = UUID.randomUUID(),
            title = "데이트",
            description = "강남 저녁",
            startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
            endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
            isAllDay = false,
            createdBy = 1L,
            syncStatus = "SYNCED",
        )

        whenever(scheduleService.createSchedule(eq(spaceId), any(), eq(email))).thenReturn(response)

        mockMvc.perform(
            post("/shared-spaces/$spaceId/schedules")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("데이트"))
            .andExpect(jsonPath("$.description").value("강남 저녁"))
    }

    @Test
    fun `GET 월별 일정을 조회할 수 있다`() {
        val responses = listOf(
            ScheduleResponse(
                id = UUID.randomUUID(),
                title = "데이트",
                description = null,
                startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
                endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
                isAllDay = false,
                createdBy = 1L,
                syncStatus = "SYNCED",
            ),
        )

        whenever(scheduleService.getSchedules(eq(spaceId), eq(2024), eq(3), eq(email)))
            .thenReturn(responses)

        mockMvc.perform(
            get("/shared-spaces/$spaceId/schedules")
                .param("year", "2024")
                .param("month", "3")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].title").value("데이트"))
    }

    @Test
    fun `GET ID로 일정을 조회할 수 있다`() {
        val scheduleId = UUID.randomUUID()
        val response = ScheduleResponse(
            id = scheduleId,
            title = "데이트",
            description = "강남 저녁",
            startDateTime = LocalDateTime.of(2024, 3, 15, 18, 0),
            endDateTime = LocalDateTime.of(2024, 3, 15, 21, 0),
            isAllDay = false,
            createdBy = 1L,
            syncStatus = "SYNCED",
        )

        whenever(scheduleService.getScheduleById(eq(spaceId), eq(scheduleId), eq(email)))
            .thenReturn(response)

        mockMvc.perform(
            get("/shared-spaces/$spaceId/schedules/$scheduleId")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("데이트"))
            .andExpect(jsonPath("$.id").value(scheduleId.toString()))
    }

    @Test
    fun `PUT 일정을 수정할 수 있다`() {
        val scheduleId = UUID.randomUUID()
        val request = ScheduleRequest(
            title = "수정된 데이트",
            startDateTime = LocalDateTime.of(2024, 3, 16, 19, 0),
            endDateTime = LocalDateTime.of(2024, 3, 16, 22, 0),
        )
        val response = ScheduleResponse(
            id = scheduleId,
            title = "수정된 데이트",
            description = null,
            startDateTime = LocalDateTime.of(2024, 3, 16, 19, 0),
            endDateTime = LocalDateTime.of(2024, 3, 16, 22, 0),
            isAllDay = false,
            createdBy = 1L,
            syncStatus = "SYNCED",
        )

        whenever(scheduleService.updateSchedule(eq(spaceId), eq(scheduleId), any(), eq(email)))
            .thenReturn(response)

        mockMvc.perform(
            put("/shared-spaces/$spaceId/schedules/$scheduleId")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("수정된 데이트"))
    }

    @Test
    fun `DELETE 일정을 삭제할 수 있다`() {
        val scheduleId = UUID.randomUUID()

        mockMvc.perform(
            delete("/shared-spaces/$spaceId/schedules/$scheduleId")
                .with(user(email))
                .with(csrf()),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `존재하지 않는 일정 조회 시 404를 반환한다`() {
        val scheduleId = UUID.randomUUID()
        whenever(scheduleService.getScheduleById(eq(spaceId), eq(scheduleId), eq(email)))
            .thenThrow(EntityNotFoundException("Schedule not found"))

        mockMvc.perform(
            get("/shared-spaces/$spaceId/schedules/$scheduleId")
                .with(user(email)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Schedule not found"))
    }

    @Test
    fun `접근 권한이 없으면 400을 반환한다`() {
        whenever(scheduleService.getSchedules(eq(spaceId), any(), any(), eq(email)))
            .thenThrow(IllegalArgumentException("Access denied: User does not belong to this shared space"))

        mockMvc.perform(
            get("/shared-spaces/$spaceId/schedules")
                .param("year", "2024")
                .param("month", "3")
                .with(user(email)),
        )
            .andExpect(status().isBadRequest)
    }
}
