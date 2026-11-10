package com.dong.daytous.dto

import java.time.Instant
import java.util.UUID

// Deliberately not a data class: credentials must never appear in generated toString().
class KisConnectRequest(val accountNumber: String, val appKey: String, val appSecret: String, val shareConsent: Boolean)
data class KisAccountResponse(val planId: UUID, val name: String, val maskedAccount: String,
    val valuation: Long?, val cash: Long?, val lastSyncedAt: Instant?, val error: String?, val canManage: Boolean)
data class KisStatusResponse(val canConnect: Boolean, val accounts: List<KisAccountResponse>, val connectablePlanIds: List<UUID>)
