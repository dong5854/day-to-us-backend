package com.dong.daytous.controller

import com.dong.daytous.config.SecurityConfig
import com.dong.daytous.config.jwt.JwtAuthenticationFilter
import com.dong.daytous.config.jwt.JwtTokenProvider
import com.dong.daytous.config.jwt.OAuth2AuthenticationSuccessHandler
import com.dong.daytous.domain.user.SyncDirection
import com.dong.daytous.dto.GoogleCalendarListEntry
import com.dong.daytous.dto.SyncSettingRequest
import com.dong.daytous.dto.SyncSettingResponse
import com.dong.daytous.service.CustomOAuth2UserService
import com.dong.daytous.service.GoogleCalendarSyncService
import com.dong.daytous.service.SyncSettingService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

@WebMvcTest(SyncSettingController::class)
@Import(SecurityConfig::class)
class SyncSettingControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @MockitoBean
    lateinit var syncSettingService: SyncSettingService

    @MockitoBean
    lateinit var googleCalendarSyncService: GoogleCalendarSyncService

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
    fun `GET 동기화 설정을 조회할 수 있다`() {
        val response = SyncSettingResponse(
            syncEnabled = true,
            syncDirection = SyncDirection.BIDIRECTIONAL,
            googleCalendarId = "primary",
        )

        whenever(syncSettingService.getSyncSetting(eq(email))).thenReturn(response)

        mockMvc.perform(
            get("/sync-settings")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.syncEnabled").value(true))
            .andExpect(jsonPath("$.syncDirection").value("BIDIRECTIONAL"))
            .andExpect(jsonPath("$.googleCalendarId").value("primary"))
    }

    @Test
    fun `PUT 동기화 설정을 변경할 수 있다`() {
        val request = SyncSettingRequest(
            syncEnabled = false,
            syncDirection = SyncDirection.APP_TO_GOOGLE,
            googleCalendarId = "work",
        )
        val response = SyncSettingResponse(
            syncEnabled = false,
            syncDirection = SyncDirection.APP_TO_GOOGLE,
            googleCalendarId = "work",
        )

        whenever(syncSettingService.updateSyncSetting(eq(email), any())).thenReturn(response)

        mockMvc.perform(
            put("/sync-settings")
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.syncEnabled").value(false))
            .andExpect(jsonPath("$.syncDirection").value("APP_TO_GOOGLE"))
            .andExpect(jsonPath("$.googleCalendarId").value("work"))
    }

    @Test
    fun `GET Google Calendar 목록을 조회할 수 있다`() {
        val calendars = listOf(
            GoogleCalendarListEntry(id = "primary", summary = "Main", primary = true),
            GoogleCalendarListEntry(id = "work@group.calendar.google.com", summary = "Work", primary = false),
        )

        whenever(syncSettingService.getGoogleCalendars(eq(email))).thenReturn(calendars)

        mockMvc.perform(
            get("/sync-settings/google-calendars")
                .with(user(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("primary"))
            .andExpect(jsonPath("$[0].primary").value(true))
            .andExpect(jsonPath("$[1].summary").value("Work"))
    }

    @Test
    fun `인증 없이 접근하면 401을 반환한다`() {
        mockMvc.perform(get("/sync-settings"))
            .andExpect(status().isUnauthorized)
    }
}
