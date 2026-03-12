package com.dong.daytous.service

import com.dong.daytous.domain.user.SyncDirection
import com.dong.daytous.domain.user.SyncSetting
import com.dong.daytous.dto.GoogleCalendarListEntry
import com.dong.daytous.dto.SyncSettingRequest
import com.dong.daytous.dto.SyncSettingResponse
import com.dong.daytous.repository.SyncSettingRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SyncSettingService(
    private val syncSettingRepository: SyncSettingRepository,
    private val userRepository: UserRepository,
    private val googleCalendarService: GoogleCalendarService,
) {
    @Transactional(readOnly = true)
    fun getSyncSetting(email: String): SyncSettingResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }

        val setting = syncSettingRepository.findByUserId(user.id)
            .orElse(null)

        return if (setting != null) {
            SyncSettingResponse(
                syncEnabled = setting.syncEnabled,
                syncDirection = setting.syncDirection,
                googleCalendarId = setting.googleCalendarId,
            )
        } else {
            // Return defaults
            SyncSettingResponse(
                syncEnabled = true,
                syncDirection = SyncDirection.BIDIRECTIONAL,
                googleCalendarId = "primary",
            )
        }
    }

    @Transactional
    fun updateSyncSetting(email: String, request: SyncSettingRequest): SyncSettingResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }

        val setting = syncSettingRepository.findByUserId(user.id)
            .map { existing ->
                existing.syncEnabled = request.syncEnabled
                existing.syncDirection = request.syncDirection
                existing.googleCalendarId = request.googleCalendarId
                existing
            }
            .orElseGet {
                SyncSetting(
                    user = user,
                    syncEnabled = request.syncEnabled,
                    syncDirection = request.syncDirection,
                    googleCalendarId = request.googleCalendarId,
                )
            }

        syncSettingRepository.save(setting)

        return SyncSettingResponse(
            syncEnabled = setting.syncEnabled,
            syncDirection = setting.syncDirection,
            googleCalendarId = setting.googleCalendarId,
        )
    }

    fun getUserByEmail(email: String) = userRepository.findByEmail(email)
        .orElseThrow { EntityNotFoundException("User not found") }

    fun getGoogleCalendars(email: String): List<GoogleCalendarListEntry> {
        val user = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }

        return googleCalendarService.listCalendars(user.id)
    }
}
