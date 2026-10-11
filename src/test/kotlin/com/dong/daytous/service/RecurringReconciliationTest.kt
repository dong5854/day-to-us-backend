package com.dong.daytous.service

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.domain.fixedexpense.*
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.*
import com.dong.daytous.repository.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecurringReconciliationTest {
    @Autowired lateinit var fixed: FixedExpenseService
    @Autowired lateinit var recurring: RecurringBudgetService
    @Autowired lateinit var budget: BudgetService
    @Autowired lateinit var entries: BudgetEntryRepository
    @Autowired lateinit var sources: FixedExpenseRepository
    @Autowired lateinit var spaces: SharedSpaceRepository
    @Autowired lateinit var users: UserRepository
    private val today get() = LocalDate.now(ZoneId.of("Asia/Seoul"))

    private fun setup(): Pair<UUID, String> {
        val space = spaces.save(SharedSpace(name = "재계산"))
        val email = "${UUID.randomUUID()}@test.com"
        users.save(User(name = "Test", email = email, role = Role.USER, provider = "google", providerId = email, sharedSpace = space))
        return space.id!! to email
    }

    private fun request(start: LocalDate) = FixedExpenseRequest("주급", BigDecimal("1000"), Frequency.WEEKLY,
        start, type = FixedTransactionType.INCOME, autoPostFrom = today.minusWeeks(4))

    @Test
    fun `앞당기면 소급 생성하고 늦추면 미수정 내역만 삭제하며 수동 변경과 삭제는 유지한다`() {
        val (space, email) = setup()
        val created = fixed.createFixedExpense(space, request(today.minusWeeks(2)), email)
        val earlier = request(today.minusWeeks(4))
        val first = fixed.previewUpdate(space, created.id, earlier, email)
        assertThat(first.added).isEqualTo(2)
        assertThat(first.deleted).isZero()
        fixed.updateFixedExpense(space, created.id, earlier.copy(reconciliationToken = first.token), email)
        assertThat(entries.findBySharedSpaceId(space)).hasSize(5)

        val byDate = entries.findBySharedSpaceId(space).associateBy { it.date }
        val editedId = byDate.getValue(today.minusWeeks(4)).id!!
        budget.updateBudgetEntry(space, editedId, BudgetEntryRequest("실제 입금", 1500.0, today.minusDays(3)), email)
        budget.deleteBudgetEntry(space, byDate.getValue(today.minusWeeks(3)).id!!, email)

        val later = request(today.minusWeeks(1))
        val second = fixed.previewUpdate(space, created.id, later, email)
        assertThat(second.deleted).isEqualTo(1)
        assertThat(second.preserved).isEqualTo(2)
        fixed.updateFixedExpense(space, created.id, later.copy(reconciliationToken = second.token), email)
        assertThat(entries.findBySharedSpaceId(space)).hasSize(3)
        assertThat(entries.findById(editedId).get().amount).isEqualTo(1500.0)

        val third = fixed.previewUpdate(space, created.id, earlier, email)
        assertThat(third.added).isEqualTo(1)
        fixed.updateFixedExpense(space, created.id, earlier.copy(reconciliationToken = third.token), email)
        recurring.postDueEntries(created.id, today.plusDays(1))
        assertThat(entries.findBySharedSpaceId(space)).hasSize(4)
        assertThat(entries.findBySharedSpaceId(space).map { it.date }).doesNotContain(today.minusWeeks(3))
        val belowFloor = fixed.previewUpdate(space, created.id, request(today.minusWeeks(10)), email)
        assertThat(belowFloor.added).isZero()
    }

    @Test
    fun `미리보기 후 수동 수정이 있으면 오래된 확인으로 변경하지 않는다`() {
        val (space, email) = setup()
        val created = fixed.createFixedExpense(space, request(today.minusWeeks(2)), email)
        val change = request(today)
        val preview = fixed.previewUpdate(space, created.id, change, email)
        val entry = entries.findBySharedSpaceId(space).first()
        budget.updateBudgetEntry(space, entry.id!!, BudgetEntryRequest("수정", 2000.0, entry.date), email)
        assertThatThrownBy {
            fixed.updateFixedExpense(space, created.id, change.copy(reconciliationToken = preview.token), email)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(entries.findBySharedSpaceId(space)).hasSize(3)
        assertThat(sources.findById(created.id).get().startDate).isEqualTo(today.minusWeeks(2))
    }

    @Test
    fun `수정 이력을 알 수 없는 기존 내역과 누락 회차는 보호한다`() {
        val (spaceId, email) = setup()
        val space = spaces.findById(spaceId).get()
        val source = sources.save(FixedExpense("기존 월급", BigDecimal("1000"), Frequency.WEEKLY,
            today.minusWeeks(2), sharedSpace = space, autoPostFrom = today.minusWeeks(2),
            postedThrough = today, occurrencesTracked = false))
        val legacy = entries.save(BudgetEntry("기존 월급", -1000.0, date = today.minusWeeks(2), sharedSpace = space, fixedExpenseId = source.id))
        val change = request(today)
        val preview = fixed.previewUpdate(spaceId, source.id!!, change, email)
        assertThat(preview.added).isZero()
        assertThat(preview.deleted).isZero()
        assertThat(preview.preserved).isEqualTo(3)
        fixed.updateFixedExpense(spaceId, source.id!!, change.copy(reconciliationToken = preview.token), email)
        recurring.postDueEntries(source.id!!, today)
        assertThat(entries.findBySharedSpaceId(spaceId).map { it.id }).containsExactly(legacy.id)
    }

    @Test
    fun `다른 공간에서는 재계산 미리보기를 할 수 없다`() {
        val (space, email) = setup()
        val created = fixed.createFixedExpense(space, request(today), email)
        val (_, otherEmail) = setup()
        assertThatThrownBy { fixed.previewUpdate(space, created.id, request(today.minusWeeks(1)), otherEmail) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
