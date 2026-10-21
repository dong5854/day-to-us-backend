package com.dong.daytous.service

import com.dong.daytous.domain.schedule.*
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.*
import com.dong.daytous.repository.*
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDateTime
import java.util.Optional

class ScheduleGoogleWriterTest {
    private val google = mock<GoogleCalendarService>()
    private val settings = mock<SyncSettingRepository>()
    private val rows = mock<ScheduleRepository>()
    private val writer = ScheduleGoogleWriter(google, settings, rows)
    private val user = User(id = 7L, name = "Test", email = "test@example.com", role = Role.USER, provider = "google", providerId = "7")
    private fun row() = Schedule("Test", startDateTime = LocalDateTime.now(), endDateTime = LocalDateTime.now().plusHours(1),
        createdBy = 1L, sharedSpace = SharedSpace("Test")).apply { googleUserId = 7L; syncStatus = SyncStatus.PENDING }

    @Test
    fun `새 일정 재시도는 현재 선택한 캘린더와 원래 계정을 사용한다`() {
        val row = row()
        whenever(settings.findByUserId(7L)).thenReturn(Optional.of(SyncSetting(user = user, googleCalendarId = "couple")))
        whenever(google.pushEvent(7L, row, "couple")).thenReturn("event")
        writer.sync(row)
        verify(google).pushEvent(7L, row, "couple")
        assertThat(row.googleCalendarId).isEqualTo("couple")
        assertThat(row.syncStatus).isEqualTo(SyncStatus.SYNCED)
    }

    @Test
    fun `이미 연결된 일정은 설정을 바꿔도 원래 캘린더에서 수정한다`() {
        val row = row().apply { googleEventId = "event"; googleCalendarId = "original" }
        whenever(settings.findByUserId(7L)).thenReturn(Optional.of(SyncSetting(user = user, googleCalendarId = "new")))
        writer.sync(row)
        verify(google).updateEvent(7L, "event", row, "original")
    }

    @Test
    fun `동기화 비활성화와 가져오기 전용에서는 생성 수정 삭제 모두 재시도하지 않는다`() {
        for (setting in listOf(SyncSetting(user = user, syncEnabled = false),
            SyncSetting(user = user, syncDirection = SyncDirection.GOOGLE_TO_APP))) {
            whenever(settings.findByUserId(7L)).thenReturn(Optional.of(setting))
            for (kind in listOf("create", "update", "delete")) {
                val row = row().apply {
                    if (kind != "create") googleEventId = "event"
                    if (kind == "delete") deletedAt = LocalDateTime.now()
                }
                writer.sync(row)
                assertThat(row.syncStatus).isEqualTo(SyncStatus.PENDING)
            }
        }
        verifyNoInteractions(google)
    }

    @Test
    fun `이미 삭제된 원격 일정의 410은 삭제 완료로 처리한다`() {
        for (status in listOf(410)) {
            val row = row().apply { googleEventId = "event"; googleCalendarId = "original"; deletedAt = LocalDateTime.now() }
            doAnswer { throw GoogleJsonResponseException(HttpResponseException.Builder(status, "gone", HttpHeaders()), null) }
                .whenever(google).deleteEvent(7L, "event", "original")
            writer.sync(row)
            assertThat(row.syncStatus).isEqualTo(SyncStatus.SYNCED)
            assertThat(row.deletedAt).isNotNull()
        }
    }

    @Test
    fun `권한 오류는 삭제 성공으로 오인하지 않고 대기 상태를 유지한다`() {
        val row = row().apply { googleEventId = "event"; googleCalendarId = "original"; deletedAt = LocalDateTime.now() }
        doAnswer { throw GoogleJsonResponseException(HttpResponseException.Builder(403, "forbidden", HttpHeaders()), null) }
            .whenever(google).deleteEvent(7L, "event", "original")
        writer.sync(row)
        assertThat(row.syncStatus).isEqualTo(SyncStatus.PENDING)
    }

    @Test
    fun `인증 토큰이 없어 생성하지 못하면 성공으로 표시하지 않는다`() {
        val row = row()
        writer.sync(row)
        assertThat(row.syncStatus).isEqualTo(SyncStatus.PENDING)
        assertThat(row.googleEventId).isNull()
    }
    @Test
    fun `연동 캘린더를 모르는 기존 일정의 404는 삭제 완료로 처리하지 않는다`() {
        val row = row().apply { googleEventId = "legacy"; deletedAt = LocalDateTime.now() }
        whenever(settings.findByUserId(7L)).thenReturn(Optional.of(SyncSetting(user = user, googleCalendarId = "selected")))
        doAnswer { throw GoogleJsonResponseException(HttpResponseException.Builder(404, "missing", HttpHeaders()), null) }
            .whenever(google).deleteEvent(7L, "legacy", "selected")
        writer.sync(row)
        assertThat(row.syncStatus).isEqualTo(SyncStatus.PENDING)
        assertThat(row.googleCalendarId).isNull()
    }

}
