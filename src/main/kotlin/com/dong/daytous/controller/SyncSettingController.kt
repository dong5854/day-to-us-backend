package com.dong.daytous.controller

import com.dong.daytous.dto.GoogleCalendarListEntry
import com.dong.daytous.dto.SyncSettingRequest
import com.dong.daytous.dto.SyncSettingResponse
import com.dong.daytous.service.SyncSettingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/sync-settings")
class SyncSettingController(
    private val syncSettingService: SyncSettingService,
) {
    @GetMapping
    fun getSyncSetting(principal: Principal): ResponseEntity<SyncSettingResponse> {
        return ResponseEntity.ok(syncSettingService.getSyncSetting(principal.name))
    }

    @PutMapping
    fun updateSyncSetting(
        @RequestBody request: SyncSettingRequest,
        principal: Principal,
    ): ResponseEntity<SyncSettingResponse> {
        return ResponseEntity.ok(syncSettingService.updateSyncSetting(principal.name, request))
    }

    @GetMapping("/google-calendars")
    fun getGoogleCalendars(principal: Principal): List<GoogleCalendarListEntry> {
        return syncSettingService.getGoogleCalendars(principal.name)
    }
}
