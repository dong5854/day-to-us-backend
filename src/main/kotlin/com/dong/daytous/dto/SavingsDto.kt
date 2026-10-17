package com.dong.daytous.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// Service validation also protects scheduler/internal callers.
data class SavingsPlanRequest(
    val name: String,
    val monthlyAmount: BigDecimal? = null,
    val firstDate: LocalDate? = null,
    val autoPostFrom: LocalDate? = null,
    val maturityDate: LocalDate? = null,
    val targetAmount: BigDecimal? = null,
    val payerEmail: String,
    val memo: String = "",
    val version: Long? = null,
    val reconciliationToken: String? = null,
)
data class SavingsRecordRequest(
    val date: LocalDate,
    val amount: BigDecimal,
    val payerEmail: String,
    val memo: String = "",
    val version: Long? = null,
)
data class SavingsPlanResponse(
    val id: UUID, val name: String, val monthlyAmount: Long?, val firstDate: LocalDate?,
    val autoPostFrom: LocalDate?, val maturityDate: LocalDate?, val targetAmount: Long?,
    val payerEmail: String, val payerName: String, val memo: String, val archived: Boolean,
    val version: Long, val balance: Long, val nextDate: LocalDate?,
)
data class SavingsRecordResponse(
    val id: String, val planId: UUID, val planName: String, val date: LocalDate, val amount: Long,
    val payerEmail: String, val payerName: String, val memo: String, val automatic: Boolean,
    val planned: Boolean, val version: Long,
)
data class SavingsMonthResponse(
    val plans: List<SavingsPlanResponse>, val records: List<SavingsRecordResponse>,
    val balanceDate: LocalDate, val deposits: Long, val withdrawals: Long, val balance: Long,
)
data class SavingsPreview(val added: Int, val removed: Int, val updated: Int, val preserved: Int, val token: String = "")
