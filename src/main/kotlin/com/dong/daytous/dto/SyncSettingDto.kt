package com.dong.daytous.dto

import com.dong.daytous.domain.user.SyncDirection

data class SyncSettingRequest(
    val syncEnabled: Boolean,
    val syncDirection: SyncDirection,
    val googleCalendarId: String = "primary",
)

data class SyncSettingResponse(
    val syncEnabled: Boolean,
    val syncDirection: SyncDirection,
    val googleCalendarId: String,
)

data class GoogleCalendarListEntry(
    val id: String,
    val summary: String,
    val primary: Boolean,
)
