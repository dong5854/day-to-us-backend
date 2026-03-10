package com.dong.daytous.service

import com.dong.daytous.config.encrypt.TokenEncryptor
import com.dong.daytous.domain.schedule.Schedule
import com.dong.daytous.domain.user.GoogleToken
import com.dong.daytous.dto.GoogleCalendarListEntry
import com.dong.daytous.repository.GoogleTokenRepository
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.UserCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

@Service
class GoogleCalendarService(
    private val googleTokenRepository: GoogleTokenRepository,
    private val tokenEncryptor: TokenEncryptor,
    @Value("\${spring.security.oauth2.client.registration.google.client-id}") private val clientId: String,
    @Value("\${spring.security.oauth2.client.registration.google.client-secret}") private val clientSecret: String,
) {
    companion object {
        private const val APPLICATION_NAME = "DayToUs"
        private val JSON_FACTORY = GsonFactory.getDefaultInstance()
        private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    }

    fun pushEvent(userId: Long, schedule: Schedule, calendarId: String = "primary"): String? {
        val calendar = buildCalendarClient(userId) ?: return null
        val event = scheduleToGoogleEvent(schedule)
        val created = calendar.events().insert(calendarId, event).execute()
        return created.id
    }

    fun updateEvent(userId: Long, googleEventId: String, schedule: Schedule, calendarId: String = "primary") {
        val calendar = buildCalendarClient(userId) ?: return
        val event = scheduleToGoogleEvent(schedule)
        calendar.events().update(calendarId, googleEventId, event).execute()
    }

    fun deleteEvent(userId: Long, googleEventId: String, calendarId: String = "primary") {
        val calendar = buildCalendarClient(userId) ?: return
        calendar.events().delete(calendarId, googleEventId).execute()
    }

    fun pullEvents(userId: Long, timeMin: LocalDateTime, timeMax: LocalDateTime, calendarId: String = "primary"): List<Event> {
        val calendar = buildCalendarClient(userId) ?: return emptyList()

        val zoneId = ZoneId.systemDefault()
        val min = com.google.api.client.util.DateTime(
            ZonedDateTime.of(timeMin, zoneId).toInstant().toEpochMilli()
        )
        val max = com.google.api.client.util.DateTime(
            ZonedDateTime.of(timeMax, zoneId).toInstant().toEpochMilli()
        )

        val events = calendar.events().list(calendarId)
            .setTimeMin(min)
            .setTimeMax(max)
            .setSingleEvents(true)
            .setOrderBy("startTime")
            .execute()

        return events.items ?: emptyList()
    }

    fun listCalendars(userId: Long): List<GoogleCalendarListEntry> {
        val calendar = buildCalendarClient(userId) ?: return emptyList()

        val calendarList = calendar.calendarList().list().execute()
        return calendarList.items?.map { entry ->
            GoogleCalendarListEntry(
                id = entry.id,
                summary = entry.summary ?: entry.id,
                primary = entry.isPrimary ?: false,
            )
        } ?: emptyList()
    }

    private fun buildCalendarClient(userId: Long): Calendar? {
        val googleToken = googleTokenRepository.findByUserId(userId).orElse(null) ?: return null

        val accessTokenValue = tokenEncryptor.decrypt(googleToken.accessToken)
        val refreshTokenValue = tokenEncryptor.decrypt(googleToken.refreshToken)

        val expirationTime = Date.from(
            googleToken.expiresAt.atZone(ZoneId.systemDefault()).toInstant()
        )

        val credentials = UserCredentials.newBuilder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRefreshToken(refreshTokenValue)
            .setAccessToken(AccessToken.newBuilder()
                .setTokenValue(accessTokenValue)
                .setExpirationTime(expirationTime)
                .build())
            .build()

        if (googleToken.expiresAt.isBefore(LocalDateTime.now())) {
            credentials.refresh()
            updateStoredTokens(googleToken, credentials)
        }

        return Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, HttpCredentialsAdapter(credentials))
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    private fun updateStoredTokens(googleToken: GoogleToken, credentials: UserCredentials) {
        googleToken.accessToken = tokenEncryptor.encrypt(credentials.accessToken.tokenValue)
        googleToken.expiresAt = credentials.accessToken.expirationTime
            ?.let { LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault()) }
            ?: LocalDateTime.now().plusHours(1)
        googleTokenRepository.save(googleToken)
    }

    private fun scheduleToGoogleEvent(schedule: Schedule): Event {
        val event = Event()
            .setSummary(schedule.title)
            .setDescription(schedule.description)

        val zoneId = ZoneId.systemDefault()

        if (schedule.isAllDay) {
            val startDate = com.google.api.client.util.DateTime(true,
                ZonedDateTime.of(schedule.startDateTime, zoneId).toInstant().toEpochMilli(), null)
            val endDate = com.google.api.client.util.DateTime(true,
                ZonedDateTime.of(schedule.endDateTime, zoneId).toInstant().toEpochMilli(), null)
            event.start = EventDateTime().setDate(startDate)
            event.end = EventDateTime().setDate(endDate)
        } else {
            val startDateTime = com.google.api.client.util.DateTime(
                ZonedDateTime.of(schedule.startDateTime, zoneId).toInstant().toEpochMilli()
            )
            val endDateTime = com.google.api.client.util.DateTime(
                ZonedDateTime.of(schedule.endDateTime, zoneId).toInstant().toEpochMilli()
            )
            event.start = EventDateTime().setDateTime(startDateTime)
            event.end = EventDateTime().setDateTime(endDateTime)
        }

        return event
    }
}
