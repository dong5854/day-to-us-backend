package com.dong.daytous.service

import com.dong.daytous.domain.fixedexpense.FixedExpense
import com.dong.daytous.domain.fixedexpense.FixedTransactionType
import com.dong.daytous.domain.fixedexpense.Frequency
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.repository.*
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecurringBudgetIntegrationTest {
    @Autowired lateinit var service: RecurringBudgetService
    @Autowired lateinit var budgetService: BudgetService
    @Autowired lateinit var sources: FixedExpenseRepository
    @Autowired lateinit var entries: BudgetEntryRepository
    @Autowired lateinit var spaces: SharedSpaceRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var em: EntityManager

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `동시 실행에서도 6월부터 각 회차를 한번만 생성한다`() {
        val space = spaces.save(SharedSpace(name = "동시 실행"))
        val source = sources.save(FixedExpense("급여", BigDecimal("1000"), Frequency.MONTHLY,
            LocalDate.of(2020, 1, 10), sharedSpace = space, type = FixedTransactionType.INCOME))
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val jobs = (1..2).map { pool.submit { service.postDueEntries(source.id!!, LocalDate.of(2026, 10, 10)) } }
            jobs.forEach { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }
            val result = entries.findBySharedSpaceId(space.id!!)
            assertThat(result).hasSize(5)
            assertThat(result.map { it.date }).containsExactlyInAnyOrder(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10))
        } finally {
            pool.shutdownNow()
            entries.deleteAll(entries.findBySharedSpaceId(space.id!!))
            sources.deleteById(source.id!!)
            spaces.deleteById(space.id!!)
        }
    }

    @Test
    fun `누락 회차를 월말 기준으로 생성하고 반복 실행해도 중복하지 않는다`() {
        val space = spaces.save(SharedSpace(name = "월말 테스트"))
        val source = sources.save(FixedExpense("월세", BigDecimal("500000"), Frequency.MONTHLY,
            LocalDate.of(2026, 1, 31), sharedSpace = space, autoPostFrom = LocalDate.of(2026, 1, 1)))
        repeat(2) {
            service.postDueEntries(source.id!!, LocalDate.of(2026, 4, 30))
            em.flush(); em.clear()
        }
        val result = entries.findBySharedSpaceId(space.id!!)
        assertThat(result.map { it.date }).containsExactlyInAnyOrder(
            LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30))
        assertThat(result.map { it.amount }).containsOnly(-500000.0)
        assertThat(result.map { it.fixedExpenseId }).containsOnly(source.id)
    }

    @Test
    fun `자동 반영 시작 전 과거와 미래 회차는 생성하지 않고 수입은 양수로 반영한다`() {
        val space = spaces.save(SharedSpace(name = "수입 테스트"))
        val source = sources.save(FixedExpense("급여", BigDecimal("3000000"), Frequency.MONTHLY,
            LocalDate.of(2020, 1, 10), sharedSpace = space, type = FixedTransactionType.INCOME,
            autoPostFrom = LocalDate.of(2026, 10, 10)))
        service.postDueEntries(source.id!!, LocalDate.of(2026, 10, 9))
        assertThat(entries.findBySharedSpaceId(space.id!!)).isEmpty()
        service.postDueEntries(source.id!!, LocalDate.of(2026, 10, 10))
        assertThat(entries.findBySharedSpaceId(space.id!!).single().amount).isEqualTo(3000000.0)
    }

    @Test
    fun `생성 내역의 날짜와 금액 수정 및 삭제 후에도 회차를 다시 만들지 않는다`() {
        val space = spaces.save(SharedSpace(name = "사후 수정"))
        val email = "${UUID.randomUUID()}@test.com"
        users.save(User(name = "Test", email = email, role = Role.USER, provider = "google",
            providerId = email, sharedSpace = space))
        val today = LocalDate.of(2026, 10, 10)
        val source = sources.save(FixedExpense("주급", BigDecimal("100000"), Frequency.WEEKLY,
            today, sharedSpace = space, type = FixedTransactionType.INCOME, autoPostFrom = today))
        service.postDueEntries(source.id!!, today)
        val original = entries.findBySharedSpaceId(space.id!!).single()
        budgetService.updateBudgetEntry(space.id!!, original.id!!,
            BudgetEntryRequest("수정한 주급", 120000.0, today.plusDays(1)), email)
        em.flush(); em.clear()
        service.postDueEntries(source.id!!, today.plusDays(1))
        val edited = entries.findBySharedSpaceId(space.id!!).single()
        assertThat(edited.amount).isEqualTo(120000.0)
        assertThat(edited.date).isEqualTo(today.plusDays(1))
        assertThat(edited.fixedExpenseId).isEqualTo(source.id)
        budgetService.deleteBudgetEntry(space.id!!, edited.id!!, email)
        service.postDueEntries(source.id!!, today.plusDays(2))
        assertThat(entries.findBySharedSpaceId(space.id!!)).isEmpty()
        service.postDueEntries(source.id!!, today.plusWeeks(1))
        assertThat(entries.findBySharedSpaceId(space.id!!).single().amount).isEqualTo(100000.0)
    }
}
