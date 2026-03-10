package com.dong.daytous.domain.schedule

enum class SyncStatus {
    SYNCED,
    PENDING,
    LOCAL_ONLY,
    GOOGLE_ONLY,
    CONFLICT,
}
