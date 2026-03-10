package com.dong.daytous.service

import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.SyncDirection
import com.dong.daytous.domain.user.SyncSetting
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.GoogleCalendarListEntry
import com.dong.daytous.dto.SyncSettingRequest
import com.dong.daytous.repository.SyncSettingRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SyncSettingServiceTest {

    @Mock
    lateinit var syncSettingRepository: SyncSettingRepository

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var googleCalendarService: GoogleCalendarService

    @InjectMocks
    lateinit var syncSettingService: SyncSettingService

    private lateinit var user: User
    private val email = "test@example.com"

    @BeforeEach
    fun setUp() {
        user = User(
            id = 1L,
            name = "Test User",
            email = email,
            role = Role.USER,
            provider = "google",
            providerId = "123",
        )
    }

    @Nested
    inner class GetSyncSetting {

        @Test
        fun `설정이 있으면 저장된 설정을 반환한다`() {
            val setting = SyncSetting(
                user = user,
                syncEnabled = false,
                syncDirection = SyncDirection.APP_TO_GOOGLE,
                googleCalendarId = "work-calendar",
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting))

            val result = syncSettingService.getSyncSetting(email)

            assertThat(result.syncEnabled).isFalse()
            assertThat(result.syncDirection).isEqualTo(SyncDirection.APP_TO_GOOGLE)
            assertThat(result.googleCalendarId).isEqualTo("work-calendar")
        }

        @Test
        fun `설정이 없으면 기본값을 반환한다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.empty())

            val result = syncSettingService.getSyncSetting(email)

            assertThat(result.syncEnabled).isTrue()
            assertThat(result.syncDirection).isEqualTo(SyncDirection.BIDIRECTIONAL)
            assertThat(result.googleCalendarId).isEqualTo("primary")
        }

        @Test
        fun `존재하지 않는 사용자면 예외가 발생한다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.empty())

            assertThatThrownBy {
                syncSettingService.getSyncSetting(email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class UpdateSyncSetting {

        @Test
        fun `기존 설정을 수정할 수 있다`() {
            val existing = SyncSetting(
                user = user,
                syncEnabled = true,
                syncDirection = SyncDirection.BIDIRECTIONAL,
                googleCalendarId = "primary",
            )
            val request = SyncSettingRequest(
                syncEnabled = false,
                syncDirection = SyncDirection.GOOGLE_TO_APP,
                googleCalendarId = "work",
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.of(existing))
            whenever(syncSettingRepository.save(any<SyncSetting>())).thenAnswer { it.arguments[0] }

            val result = syncSettingService.updateSyncSetting(email, request)

            assertThat(result.syncEnabled).isFalse()
            assertThat(result.syncDirection).isEqualTo(SyncDirection.GOOGLE_TO_APP)
            assertThat(result.googleCalendarId).isEqualTo("work")
        }

        @Test
        fun `설정이 없으면 새로 생성한다`() {
            val request = SyncSettingRequest(
                syncEnabled = true,
                syncDirection = SyncDirection.APP_TO_GOOGLE,
                googleCalendarId = "personal",
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(syncSettingRepository.findByUserId(1L)).thenReturn(Optional.empty())
            whenever(syncSettingRepository.save(any<SyncSetting>())).thenAnswer { it.arguments[0] }

            val result = syncSettingService.updateSyncSetting(email, request)

            assertThat(result.syncEnabled).isTrue()
            assertThat(result.syncDirection).isEqualTo(SyncDirection.APP_TO_GOOGLE)
            assertThat(result.googleCalendarId).isEqualTo("personal")
        }
    }

    @Nested
    inner class GetGoogleCalendars {

        @Test
        fun `Google Calendar 목록을 조회할 수 있다`() {
            val calendars = listOf(
                GoogleCalendarListEntry(id = "primary", summary = "My Calendar", primary = true),
                GoogleCalendarListEntry(id = "work@group.calendar.google.com", summary = "Work", primary = false),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(googleCalendarService.listCalendars(1L)).thenReturn(calendars)

            val result = syncSettingService.getGoogleCalendars(email)

            assertThat(result).hasSize(2)
            assertThat(result[0].primary).isTrue()
            assertThat(result[1].summary).isEqualTo("Work")
        }
    }
}
