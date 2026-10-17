package com.dong.daytous.service

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

@SpringBootTest(properties = ["savings.auto-post.enabled=false"])
@ActiveProfiles("test")
@Transactional
class SavingsServiceTest {
    @Autowired lateinit var service: SavingsService
    @Autowired lateinit var spaces: SharedSpaceRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var records: SavingsRecordRepository
    @Autowired lateinit var plans: SavingsPlanRepository
    @Autowired lateinit var budgets: BudgetEntryRepository
    private val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun fixture(): Pair<UUID, String> {
        val space = spaces.save(SharedSpace("저축 테스트"))
        val email = "${UUID.randomUUID()}@test.com"
        users.save(User(name = "납입자", email = email, role = Role.USER, provider = "google", providerId = email, sharedSpace = space))
        return space.id!! to email
    }
    private fun request(email: String) = SavingsPlanRequest("적금", BigDecimal(50000),
        today.minusMonths(2).withDayOfMonth(1), today.minusMonths(2).withDayOfMonth(1), payerEmail = email)

    @Test fun `등록 즉시 지난 회차가 한번씩 생성되고 가계부 합계와 분리된다`() {
        val (space, email) = fixture()
        val id = service.create(space, request(email), email)
        repeat(3) { service.postDue(id, today) }
        assertThat(records.findByPlanId(id)).hasSize(3)
        val month = service.month(space, today.year, today.monthValue, email)
        assertThat(month.deposits).isEqualTo(50000)
        assertThat(month.balance).isEqualTo(150000)
        assertThat(budgets.findBySharedSpaceId(space)).isEmpty()
        val future = today.plusMonths(1)
        val next = service.month(space, future.year, future.monthValue, email)
        assertThat(next.records).hasSize(1)
        assertThat(next.records.single().planned).isTrue()
        assertThat(next.deposits).isZero()
    }

    @Test fun `수정 및 삭제한 자동 기록은 날짜 변경과 배치 이후에도 보존된다`() {
        val (space, email) = fixture()
        val r = request(email)
        val id = service.create(space, r, email)
        val original = records.findByPlanId(id).sortedBy { it.date }
        service.updateRecord(space, id, original[0].id!!,
            SavingsRecordRequest(today, BigDecimal(60000), email, version = original[0].version), email)
        service.deleteRecord(space, id, original[1].id!!, email)
        val p = plans.findById(id).orElseThrow()
        val change = r.copy(firstDate = today.withDayOfMonth(1), version = p.version)
        val preview = service.preview(space, id, change, email)
        assertThat(preview.preserved).isEqualTo(2)
        service.update(space, id, change.copy(reconciliationToken = service.preview(space, id, change, email).token), email)
        service.postDue(id, today)
        val entries = records.findByPlanId(id)
        assertThat(entries).hasSize(3)
        assertThat(entries.single { it.edited }.amount).isEqualTo(60000)
        assertThat(entries.count { it.deleted }).isEqualTo(1)
    }

    @Test fun `첫 예정일을 앞당기면 추가하고 뒤로 미루면 미수정 회차만 제거한다`() {
        val (space, email) = fixture()
        val r = request(email)
        val id = service.create(space, r, email)
        val p = plans.findById(id).orElseThrow()
        val later = r.copy(firstDate = today.withDayOfMonth(1), version = p.version)
        assertThat(service.preview(space, id, later, email).removed).isEqualTo(2)
        service.update(space, id, later.copy(reconciliationToken = service.preview(space, id, later, email).token), email)
        assertThat(records.findByPlanId(id)).hasSize(1)
        val earlier = r.copy(version = p.version)
        assertThat(service.preview(space, id, earlier, email).added).isEqualTo(2)
        service.update(space, id, earlier.copy(reconciliationToken = service.preview(space, id, earlier, email).token), email)
        assertThat(records.findByPlanId(id)).hasSize(3)
    }

    @Test fun `미리보기 이후 다른 사용자가 내역을 변경하면 저장을 막는다`() {
        val (space, email) = fixture()
        val r = request(email)
        val id = service.create(space, r, email)
        val update = r.copy(version = plans.findById(id).orElseThrow().version)
        val preview = service.preview(space, id, update, email)
        service.deleteRecord(space, id, records.findByPlanId(id).first().id!!, email)
        assertThatThrownBy { service.update(space, id, update.copy(reconciliationToken = preview.token), email) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun `월말 윤년과 만기일을 원래 납입일 기준으로 계산한다`() {
        val (space, email) = fixture()
        val id = service.create(space, SavingsPlanRequest("월말", BigDecimal(1000), LocalDate.of(2024, 1, 31),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 4, 30), payerEmail = email), email)
        assertThat(records.findByPlanId(id).map { it.date }).containsExactlyInAnyOrder(
            LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31), LocalDate.of(2024, 4, 30))
        val feb = service.month(space, 2024, 2, email)
        assertThat(feb.balance).isEqualTo(2000)
        assertThat(feb.balanceDate).isEqualTo(LocalDate.of(2024, 2, 29))
    }

    @Test fun `자유 저축의 인출과 종료 및 공간 접근을 검증한다`() {
        val (space, email) = fixture()
        val (_, outsider) = fixture()
        val id = service.create(space, SavingsPlanRequest("여행", payerEmail = email), email)
        assertThatThrownBy { service.month(space, today.year, today.monthValue, outsider) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.createRecord(space, id, SavingsRecordRequest(today, BigDecimal(1), outsider), email) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.createRecord(space, id, SavingsRecordRequest(today.plusDays(1), BigDecimal(1), email), email) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.createRecord(space, id, SavingsRecordRequest(today, BigDecimal("1.5"), email), email) }.isInstanceOf(IllegalArgumentException::class.java)
        service.createRecord(space, id, SavingsRecordRequest(today, BigDecimal(10000), email), email)
        service.createRecord(space, id, SavingsRecordRequest(today, BigDecimal(-3000), email), email)
        service.archive(space, id, email)
        service.archive(space, id, email)
        val month = service.month(space, today.year, today.monthValue, email)
        assertThat(month.balance).isEqualTo(7000)
        assertThat(month.withdrawals).isEqualTo(3000)
        assertThat(month.records).hasSize(2)
        assertThat(month.plans.single().archived).isTrue()
        assertThatThrownBy { service.createRecord(space, id, SavingsRecordRequest(today, BigDecimal(1), email), email) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `동시 배치 실행도 같은 회차를 중복 생성하지 않는다`() {
        val (space, email) = fixture()
        val id = service.create(space, SavingsPlanRequest("동시성", BigDecimal(1000), today.plusMonths(1), today, payerEmail = email), email)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val jobs = (1..2).map { executor.submit { service.postDue(id, today.plusMonths(2)) } }
            jobs.forEach { it.get(15, java.util.concurrent.TimeUnit.SECONDS) }
            assertThat(records.findByPlanId(id)).hasSize(2)
        } finally {
            executor.shutdownNow()
            records.deleteAll(records.findByPlanId(id)); plans.deleteById(id)
            users.delete(users.findByEmail(email).orElseThrow()); spaces.deleteById(space)
        }
    }
}
