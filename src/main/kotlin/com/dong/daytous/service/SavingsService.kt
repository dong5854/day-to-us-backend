package com.dong.daytous.service

import com.dong.daytous.domain.savings.*
import com.dong.daytous.dto.*
import com.dong.daytous.repository.*
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.security.MessageDigest

@Service
@Transactional
class SavingsService(
    private val plans: SavingsPlanRepository,
    private val records: SavingsRecordRepository,
    private val users: UserRepository,
) {
    private fun today() = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun member(spaceId: UUID, email: String) = users.findByEmail(email).orElseThrow {
        EntityNotFoundException("사용자를 찾을 수 없습니다")
    }.also { require(it.sharedSpace?.id == spaceId) { "공유 공간의 멤버만 사용할 수 있습니다" } }

    private fun plan(spaceId: UUID, id: UUID, email: String): SavingsPlan {
        member(spaceId, email)
        return (plans.lockById(id) ?: throw EntityNotFoundException("저축을 찾을 수 없습니다"))
            .also { require(it.sharedSpace.id == spaceId) { "다른 공유 공간의 저축입니다" } }
    }
    private fun money(value: BigDecimal): Long {
        require(value.stripTrailingZeros().scale() <= 0 && value.abs() <= BigDecimal.valueOf(MAX_AMOUNT)) { "금액은 한도 내 원 단위 정수여야 합니다" }
        return value.longValueExact()
    }
    private fun validate(r: SavingsPlanRequest) {
        require(r.name.isNotBlank() && r.name.length <= 100) { "이름은 1~100자로 입력하세요" }
        require(r.memo.length <= 1000) { "메모는 1000자 이하여야 합니다" }
        require(r.targetAmount == null || money(r.targetAmount) in 1..MAX_AMOUNT) { "목표 금액을 확인하세요" }
        if (r.monthlyAmount != null) {
            require(money(r.monthlyAmount) in 1..MAX_AMOUNT && r.firstDate != null && r.autoPostFrom != null) { "정기 금액과 시작일이 필요합니다" }
            require(r.firstDate.year in 2000..2100 && r.autoPostFrom.year in 2000..2100) { "날짜는 2000~2100년으로 입력하세요" }
            require(r.maturityDate == null || r.maturityDate >= r.firstDate) { "만기일은 첫 납입일 이후여야 합니다" }
        } else require(r.firstDate == null && r.autoPostFrom == null) { "자유 납입에는 정기 날짜를 설정할 수 없습니다" }
        require(r.maturityDate == null || r.maturityDate.year in 2000..2100) { "만기일을 확인하세요" }
    }
    private fun apply(p: SavingsPlan, r: SavingsPlanRequest) {
        validate(r)
        val payer = member(p.sharedSpace.id!!, r.payerEmail)
        p.name = r.name.trim(); p.monthlyAmount = r.monthlyAmount?.let(::money); p.firstDate = r.firstDate
        p.autoPostFrom = r.autoPostFrom; p.maturityDate = r.maturityDate; p.targetAmount = r.targetAmount?.let(::money)
        p.payerEmail = payer.email; p.payerName = payer.name; p.memo = r.memo.trim()
    }

    fun create(spaceId: UUID, r: SavingsPlanRequest, email: String): UUID {
        val user = member(spaceId, email)
        val p = SavingsPlan(sharedSpace = user.sharedSpace!!, name = r.name, payerEmail = email, payerName = user.name)
        apply(p, r)
        plans.saveAndFlush(p)
        reconcile(p, today(), true)
        return p.id!!
    }
    fun update(spaceId: UUID, id: UUID, r: SavingsPlanRequest, email: String) {
        val p = plan(spaceId, id, email)
        require(!p.archived) { "종료된 저축은 수정할 수 없습니다" }
        require(r.version == p.version) { "다른 사용자가 수정했습니다. 새로고침 후 다시 시도하세요" }
        require(r.reconciliationToken == token(p, r)) { "내역이 변경되었습니다. 변경 내용을 다시 확인해 주세요" }
        apply(p, r)
        reconcile(p, today(), true)
    }
    private fun token(p: SavingsPlan, r: SavingsPlanRequest): String {
        val history = records.findByPlanId(p.id!!).sortedBy { it.id.toString() }
            .map { listOf(it.id, it.version, it.date, it.amount, it.edited, it.deleted, it.occurrenceMonth) }
        val snapshot = listOf(p.id, p.version, today(), r.copy(reconciliationToken = null), history).toString()
        return MessageDigest.getInstance("SHA-256").digest(snapshot.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun preview(spaceId: UUID, id: UUID, r: SavingsPlanRequest, email: String): SavingsPreview {
        val original = plan(spaceId, id, email)
        require(!original.archived)
        val copy = SavingsPlan(sharedSpace = original.sharedSpace, name = original.name,
            payerEmail = original.payerEmail, payerName = original.payerName)
        copy.id = original.id
        apply(copy, r)
        return reconcile(copy, today(), false).copy(token = token(original, r))
    }
    // End future installments without destroying the historical ledger.
    fun archive(spaceId: UUID, id: UUID, email: String) {
        val p = plan(spaceId, id, email)
        if (p.archived) return
        reconcile(p, today(), true)
        p.archived = true
    }

    fun postDue(id: UUID, through: LocalDate) {
        val p = plans.lockById(id) ?: return
        if (!p.archived) reconcile(p, through, true)
    }
    private fun dates(p: SavingsPlan, through: LocalDate): Map<LocalDate, LocalDate> {
        if (p.monthlyAmount == null || p.firstDate == null || p.autoPostFrom == null || p.archived) return emptyMap()
        val first = p.firstDate!!
        val end = minOf(through, p.maturityDate ?: through)
        var month = YearMonth.from(maxOf(first, p.autoPostFrom!!))
        val result = linkedMapOf<LocalDate, LocalDate>()
        while (!month.atDay(1).isAfter(end)) {
            val date = month.atDay(minOf(first.dayOfMonth, month.lengthOfMonth()))
            if (date >= first && date >= p.autoPostFrom && date <= end) result[month.atDay(1)] = date
            month = month.plusMonths(1)
        }
        return result
    }
    private fun reconcile(p: SavingsPlan, through: LocalDate, write: Boolean): SavingsPreview {
        val expected = dates(p, through)
        val existing = records.findByPlanId(p.id!!).filter { it.occurrenceMonth != null }
        val byMonth = existing.associateBy { it.occurrenceMonth }
        val removed = existing.filter { !it.edited && !it.deleted && it.occurrenceMonth !in expected }
        val changed = existing.filter { !it.edited && !it.deleted && it.occurrenceMonth in expected &&
            (it.date != expected[it.occurrenceMonth] || it.amount != p.monthlyAmount || it.payerEmail != p.payerEmail) }
        val added = expected.filterKeys { it !in byMonth }
        if (write) {
            records.deleteAll(removed)
            changed.forEach { it.date = expected.getValue(it.occurrenceMonth!!); it.amount = p.monthlyAmount!!
                it.payerEmail = p.payerEmail; it.payerName = p.payerName }
            records.saveAll(added.map { (month, date) -> SavingsRecord(p, date, p.monthlyAmount!!,
                p.payerEmail, p.payerName, occurrenceMonth = month) })
        }
        return SavingsPreview(added.size, removed.size, changed.size, existing.count { it.edited || it.deleted })
    }

    private fun recordRequest(spaceId: UUID, r: SavingsRecordRequest) {
        require(money(r.amount) != 0L) { "금액은 0보다 큰 원 단위로 입력하세요" }
        require(r.date.year in 2000..2100 && r.date <= today()) { "납입·인출 기록은 오늘까지 입력할 수 있습니다" }
        require(r.memo.length <= 1000) { "메모는 1000자 이하여야 합니다" }
        member(spaceId, r.payerEmail)
    }
    fun createRecord(spaceId: UUID, id: UUID, r: SavingsRecordRequest, email: String) {
        val p = plan(spaceId, id, email)
        require(!p.archived) { "종료된 저축에는 기록을 추가할 수 없습니다" }
        recordRequest(spaceId, r)
        val payer = member(spaceId, r.payerEmail)
        records.save(SavingsRecord(p, r.date, money(r.amount), payer.email, payer.name, r.memo))
    }
    fun updateRecord(spaceId: UUID, id: UUID, recordId: UUID, r: SavingsRecordRequest, email: String) {
        plan(spaceId, id, email)
        recordRequest(spaceId, r)
        val entry = records.findById(recordId).orElseThrow()
        require(entry.plan.id == id && !entry.deleted)
        require(entry.version == r.version) { "다른 사용자가 수정했습니다. 새로고침 후 다시 시도하세요" }
        val payer = member(spaceId, r.payerEmail)
        entry.date = r.date; entry.amount = money(r.amount); entry.memo = r.memo
        entry.payerEmail = payer.email; entry.payerName = payer.name; entry.edited = true
    }
    fun deleteRecord(spaceId: UUID, id: UUID, recordId: UUID, email: String) {
        plan(spaceId, id, email)
        val entry = records.findById(recordId).orElseThrow()
        require(entry.plan.id == id)
        entry.deleted = true // Keep the occurrence identity so the scheduler cannot recreate it.
    }
    @Transactional(readOnly = true)
    fun month(spaceId: UUID, year: Int, month: Int, email: String): SavingsMonthResponse {
        member(spaceId, email)
        require(year in 2000..2100 && month in 1..12) { "조회할 월을 확인하세요" }
        val ym = YearMonth.of(year, month)
        val cutoff = minOf(today(), ym.atEndOfMonth())
        val allPlans = plans.findBySharedSpaceId(spaceId)
        val all = records.findForSpace(spaceId)
        val visible = all.filter { !it.deleted }
        val monthly = visible.filter { YearMonth.from(it.date) == ym }
        val actual = monthly.map { SavingsRecordResponse(it.id.toString(), it.plan.id!!, it.plan.name,
            it.date, it.amount, it.payerEmail, it.payerName, it.memo, it.occurrenceMonth != null, false, it.version) }
        val planned = allPlans.flatMap { p ->
            val tracked = all.filter { it.plan.id == p.id }.mapNotNull { it.occurrenceMonth }.toSet()
            dates(p, ym.atEndOfMonth()).filter { (key, date) -> date > today() && YearMonth.from(date) == ym && key !in tracked }
                .map { (key, date) -> SavingsRecordResponse("${p.id}:$key", p.id!!, p.name, date,
                    p.monthlyAmount!!, p.payerEmail, p.payerName, "", true, true, 0) }
        }
        val balances = visible.filter { it.date <= cutoff }.groupBy { it.plan.id }.mapValues { (_, list) -> list.sumOf { it.amount } }
        return SavingsMonthResponse(allPlans.map { p -> SavingsPlanResponse(p.id!!, p.name, p.monthlyAmount,
            p.firstDate, p.autoPostFrom, p.maturityDate, p.targetAmount, p.payerEmail, p.payerName,
            p.memo, p.archived, p.version, balances[p.id] ?: 0,
            dates(p, maxOf(today(), p.firstDate ?: today(), p.autoPostFrom ?: today()).plusMonths(1)).values.firstOrNull { it > today() }) }, (actual + planned).sortedBy { it.date }, cutoff,
            monthly.filter { it.amount > 0 }.sumOf { it.amount }, -monthly.filter { it.amount < 0 }.sumOf { it.amount }, balances.values.sum())
    }
    companion object { const val MAX_AMOUNT = 1_000_000_000_000L }
}
