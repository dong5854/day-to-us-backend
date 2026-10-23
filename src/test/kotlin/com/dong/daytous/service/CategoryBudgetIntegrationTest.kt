package com.dong.daytous.service

import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.domain.fixedexpense.FixedExpense
import com.dong.daytous.domain.fixedexpense.Frequency
import com.dong.daytous.dto.*
import com.dong.daytous.repository.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

@SpringBootTest(properties = ["savings.auto-post.enabled=false"])
@ActiveProfiles("test")
@Transactional
class CategoryBudgetIntegrationTest {
    @Autowired lateinit var service: ExpenseCategoryService
    @Autowired lateinit var spaces: SharedSpaceRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var entries: BudgetEntryRepository
    @Autowired lateinit var categories: ExpenseCategoryRepository
    @Autowired lateinit var fixed: FixedExpenseRepository
    private val month = YearMonth.now(ZoneId.of("Asia/Seoul"))
    private fun fixture(): Pair<UUID, String> {
        val space = spaces.save(SharedSpace("카테고리 테스트"))
        val email = "${UUID.randomUUID()}@test.com"
        users.save(User(name = "멤버", email = email, role = Role.USER, provider = "google", providerId = email, sharedSpace = space))
        return space.id!! to email
    }
    private fun budget(space: UUID, email: String, id: UUID, m: YearMonth = month) =
        service.overview(space, m.toString(), email).rows.single { it.category?.id == id }.history.last().budget

    @Test fun `반복 예산은 과거와 이후 개별 예산을 덮어쓰지 않고 해제와 0원을 구분한다`() {
        val (space, email) = fixture()
        val id = service.createCategory(space, ExpenseCategoryRequest("식비"), email).id
        service.setBudget(space, id, month.toString(), CategoryBudgetRequest(400000, true), email)
        service.setBudget(space, id, month.plusMonths(2).toString(), CategoryBudgetRequest(600000), email)
        service.setBudget(space, id, month.plusMonths(1).toString(), CategoryBudgetRequest(300000, true), email)
        assertThat(budget(space, email, id)).isEqualTo(400000)
        assertThat(budget(space, email, id, month.plusMonths(1))).isEqualTo(300000)
        assertThat(budget(space, email, id, month.plusMonths(2))).isEqualTo(600000)
        assertThat(budget(space, email, id, month.plusMonths(3))).isEqualTo(300000)
        service.setBudget(space, id, month.plusMonths(2).toString(), CategoryBudgetRequest(null), email)
        assertThat(budget(space, email, id, month.plusMonths(2))).isNull()
        service.setBudget(space, id, month.plusMonths(2).toString(), CategoryBudgetRequest(0), email)
        assertThat(budget(space, email, id, month.plusMonths(2))).isZero()
        assertThatThrownBy { service.setBudget(space, id, month.minusMonths(1).toString(), CategoryBudgetRequest(100, true), email) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun `지출은 수입과 고정 원본을 제외하고 미지정과 보관 이력까지 합산한다`() {
        val (space, email) = fixture()
        val id = service.createCategory(space, ExpenseCategoryRequest("식비"), email).id
        val category = categories.findById(id).orElseThrow()
        val sharedSpace = spaces.findById(space).orElseThrow()
        entries.save(BudgetEntry("식사", -10000.0, category = category, date = month.atDay(1), sharedSpace = sharedSpace))
        entries.save(BudgetEntry("수입", 90000.0, category = category, date = month.atDay(2), sharedSpace = sharedSpace))
        entries.save(BudgetEntry("분류 없음", -3000.0, date = month.atEndOfMonth(), sharedSpace = sharedSpace))
        entries.save(BudgetEntry("지난달", -2000.0, category = category, date = month.minusMonths(1).atEndOfMonth(), sharedSpace = sharedSpace))
        val source = fixed.save(FixedExpense("정기 식사", BigDecimal(50000), Frequency.MONTHLY, month.atDay(1), category = category, sharedSpace = sharedSpace))
        assertThatThrownBy { service.archive(space, id, true, email) }.isInstanceOf(IllegalArgumentException::class.java)
        fixed.delete(source)
        fixed.flush()
        service.archive(space, id, true, email)
        val overview = service.overview(space, month.toString(), email)
        assertThat(overview.totalSpent).isEqualByComparingTo("13000")
        assertThat(overview.rows.single { it.category?.id == id }.history.last().spent).isEqualByComparingTo("10000")
        assertThat(overview.rows.single { it.category == null }.history.last().spent).isEqualByComparingTo("3000")
        assertThat(service.getAllCategories(space, email)).isEmpty()
        assertThatThrownBy { service.deleteCategory(space, id, email) }.isInstanceOf(IllegalArgumentException::class.java)
        service.archive(space, id, false, email)
        assertThat(service.getAllCategories(space, email)).hasSize(1)
    }

    @Test fun `순서와 이름은 공간 단위로 관리하고 다른 공간의 조회와 변경을 차단한다`() {
        val (space, email) = fixture()
        val first = service.createCategory(space, ExpenseCategoryRequest(" 식비 "), email)
        val second = service.createCategory(space, ExpenseCategoryRequest("교통"), email)
        service.reorder(space, listOf(second.id, first.id), email)
        assertThat(service.getAllCategories(space, email).map { it.id }).containsExactly(second.id, first.id)
        service.rename(space, first.id, ExpenseCategoryRequest("외식"), email)
        assertThat(service.getAllCategories(space, email).last().name).isEqualTo("외식")
        assertThatThrownBy { service.rename(space, first.id, ExpenseCategoryRequest("교통"), email) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.reorder(space, listOf(first.id, first.id), email) }.isInstanceOf(IllegalArgumentException::class.java)
        val (other, otherEmail) = fixture()
        assertThatThrownBy { service.overview(space, month.toString(), otherEmail) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.setBudget(other, first.id, month.toString(), CategoryBudgetRequest(100), otherEmail) }.isInstanceOf(jakarta.persistence.EntityNotFoundException::class.java)
        assertThatThrownBy { service.archive(other, first.id, true, otherEmail) }.isInstanceOf(jakarta.persistence.EntityNotFoundException::class.java)
        assertThatThrownBy { service.createCategory(space, ExpenseCategoryRequest(" "), email) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.setBudget(space, first.id, month.toString(), CategoryBudgetRequest(-1), email) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
