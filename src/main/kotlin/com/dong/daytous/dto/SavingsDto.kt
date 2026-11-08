package com.dong.daytous.dto

import com.dong.daytous.domain.savings.SavingsKind
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
    val kind: SavingsKind? = null,
)
data class SavingsRecordRequest(
    val date: LocalDate,
    val amount: BigDecimal,
    val payerEmail: String,
    val memo: String = "",
    val version: Long? = null,
    val confirmed: Boolean = false,
)
data class SavingsPlanResponse(
    val id: UUID, val name: String, val monthlyAmount: Long?, val firstDate: LocalDate?,
    val autoPostFrom: LocalDate?, val maturityDate: LocalDate?, val targetAmount: Long?,
    val payerEmail: String, val payerName: String, val memo: String, val archived: Boolean,
    val version: Long, val balance: Long, val nextDate: LocalDate?,
    val kind: SavingsKind = SavingsKind.GENERAL,
)
data class SavingsRecordResponse(
    val id: String, val planId: UUID, val planName: String, val date: LocalDate, val amount: Long,
    val payerEmail: String, val payerName: String, val memo: String, val automatic: Boolean,
    val planned: Boolean, val version: Long, val confirmed: Boolean = false, val kind: SavingsKind = SavingsKind.GENERAL,
)
data class SavingsMonthResponse(
    val plans: List<SavingsPlanResponse>, val records: List<SavingsRecordResponse>,
    val balanceDate: LocalDate, val deposits: Long, val withdrawals: Long, val balance: Long,
)
data class SavingsPreview(val added: Int, val removed: Int, val updated: Int, val preserved: Int, val token: String = "")

data class PensionAccountSummary(
    val planId: UUID, val ownerEmail: String, val kind: SavingsKind,
    val confirmedDeposits: Long, val unconfirmedCount: Int, val hasWithdrawals: Boolean,
)
