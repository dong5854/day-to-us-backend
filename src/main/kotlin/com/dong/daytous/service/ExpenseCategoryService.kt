package com.dong.daytous.service

import com.dong.daytous.domain.category.ExpenseCategory
import com.dong.daytous.dto.ExpenseCategoryRequest
import com.dong.daytous.dto.ExpenseCategoryResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.ExpenseCategoryRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ExpenseCategoryService(
    private val expenseCategoryRepository: ExpenseCategoryRepository,
    private val sharedSpaceRepository: SharedSpaceRepository,
    private val userRepository: UserRepository,
    private val budgetEntryRepository: BudgetEntryRepository,
    private val fixedExpenseRepository: com.dong.daytous.repository.FixedExpenseRepository,
    private val categoryBudgetRepository: com.dong.daytous.repository.CategoryBudgetRepository,
) {
    @Transactional
    fun createCategory(
        spaceId: UUID,
        request: ExpenseCategoryRequest,
        email: String,
    ): ExpenseCategoryResponse {
        checkAccess(spaceId, email)

        val name = validName(request.name)
        if (expenseCategoryRepository.existsByNameAndSharedSpaceId(name, spaceId)) {
            throw IllegalArgumentException("이미 해당 공유 공간에 동일한 이름의 카테고리가 존재합니다.")
        }

        val sharedSpace = sharedSpaceRepository.findById(spaceId)
            .orElseThrow { EntityNotFoundException("SharedSpace with id $spaceId not found") }
        val category = ExpenseCategory(name = name, sharedSpace = sharedSpace, sortOrder = (expenseCategoryRepository.findBySharedSpaceId(spaceId).maxOfOrNull { it.sortOrder } ?: -1) + 1)
        return expenseCategoryRepository.save(category).toResponse()
    }

    @Transactional(readOnly = true)
    fun getAllCategories(
        spaceId: UUID,
        email: String,
    ): List<ExpenseCategoryResponse> {
        checkAccess(spaceId, email)
        return sortedCategories(spaceId).filter { !it.archived }.map { it.toResponse() }
    }

    @Transactional
    fun deleteCategory(
        spaceId: UUID,
        categoryId: UUID,
        email: String,
    ) {
        checkAccess(spaceId, email)
        val category = expenseCategoryRepository.findByIdAndSharedSpaceId(categoryId, spaceId)
            ?: throw EntityNotFoundException("ExpenseCategory with id $categoryId not found in space $spaceId")
        require(!fixedExpenseRepository.existsByCategoryId(categoryId)) { "연결된 고정 수입·지출의 카테고리를 먼저 변경해주세요." }
        require(!budgetEntryRepository.existsByCategoryId(categoryId) && !categoryBudgetRepository.existsByCategoryId(categoryId)) { "사용 이력이 있는 카테고리는 삭제 대신 보관해주세요." }
        expenseCategoryRepository.delete(category)
    }

    private fun validName(value: String): String {
        val name = value.trim()
        require(name.length in 1..20) { "카테고리 이름은 1~20자로 입력해주세요." }
        return name
    }

    private fun sortedCategories(spaceId: UUID) = expenseCategoryRepository.findBySharedSpaceId(spaceId)
        .sortedWith(compareBy<ExpenseCategory> { it.sortOrder }.thenBy { it.id })

    @Transactional(readOnly = true)
    fun getIncludingArchived(spaceId: UUID, email: String): List<ExpenseCategoryResponse> {
        checkAccess(spaceId, email)
        return sortedCategories(spaceId).map { it.toResponse() }
    }

    @Transactional
    fun rename(spaceId: UUID, id: UUID, request: ExpenseCategoryRequest, email: String): ExpenseCategoryResponse {
        checkAccess(spaceId, email)
        val category = expenseCategoryRepository.findLocked(id, spaceId) ?: throw EntityNotFoundException("카테고리를 찾을 수 없습니다.")
        val name = validName(request.name)
        require(category.name == name || !expenseCategoryRepository.existsByNameAndSharedSpaceId(name, spaceId)) { "이미 같은 이름의 카테고리가 있어요." }
        category.name = name
        return category.toResponse()
    }

    @Transactional
    fun reorder(spaceId: UUID, ids: List<UUID>, email: String) {
        checkAccess(spaceId, email)
        val categories = sortedCategories(spaceId).filter { !it.archived }
        require(ids.size == ids.toSet().size && ids.toSet() == categories.map { it.id }.toSet()) { "카테고리 목록이 변경됐어요. 새로고침 후 다시 시도해주세요." }
        categories.forEach { it.sortOrder = ids.indexOf(it.id) }
    }

    @Transactional
    fun archive(spaceId: UUID, id: UUID, archived: Boolean, email: String) {
        checkAccess(spaceId, email)
        val category = expenseCategoryRepository.findLocked(id, spaceId) ?: throw EntityNotFoundException("카테고리를 찾을 수 없습니다.")
        require(!archived || !fixedExpenseRepository.existsByCategoryId(id)) { "연결된 고정 수입·지출의 카테고리를 먼저 변경해주세요." }
        category.archived = archived
    }

    private fun parseMonth(value: String): java.time.YearMonth {
        val month = try { java.time.YearMonth.parse(value) } catch (_: java.time.DateTimeException) { throw IllegalArgumentException("월 형식은 YYYY-MM이어야 합니다.") }
        require(month.year in 1900..9999) { "지원하지 않는 연도입니다." }
        return month
    }

    @Transactional
    fun setBudget(spaceId: UUID, id: UUID, monthValue: String, request: com.dong.daytous.dto.CategoryBudgetRequest, email: String) {
        checkAccess(spaceId, email)
        val category = expenseCategoryRepository.findLocked(id, spaceId) ?: throw EntityNotFoundException("카테고리를 찾을 수 없습니다.")
        require(!category.archived) { "보관한 카테고리는 복원 후 예산을 설정해주세요." }
        require(request.amount == null || request.amount in 0..999_999_999_999L) { "예산은 0~999,999,999,999원으로 입력해주세요." }
        val month = parseMonth(monthValue)
        require(!request.repeat || month >= java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul"))) { "과거 월은 해당 월의 예산만 변경할 수 있어요." }
        // A monthly override always wins; recurring changes never rewrite future explicit overrides.
        fun save(recurring: Boolean) {
            val rule = categoryBudgetRepository.findByCategoryIdAndMonthAndRecurring(id, month.atDay(1), recurring)
                ?: com.dong.daytous.domain.category.CategoryBudget(category, month.atDay(1), recurring, request.amount)
            rule.amount = request.amount
            categoryBudgetRepository.save(rule)
        }
        save(false)
        if (request.repeat) save(true)
    }

    @Transactional(readOnly = true)
    fun overview(spaceId: UUID, monthValue: String, email: String): com.dong.daytous.dto.CategoryOverview {
        checkAccess(spaceId, email)
        val month = parseMonth(monthValue)
        val months = (5L downTo 0L).map { month.minusMonths(it) }
        val entries = budgetEntryRepository.findBySharedSpaceIdAndDateBetween(spaceId, months.first().atDay(1), month.atEndOfMonth())
            .filter { it.amount < 0 }
        val totals = entries.groupBy { it.category?.id to java.time.YearMonth.from(it.date) }
            .mapValues { (_, records) -> records.fold(java.math.BigDecimal.ZERO) { sum, entry -> sum + java.math.BigDecimal.valueOf(-entry.amount) } }
        val rules = categoryBudgetRepository.findByCategorySharedSpaceId(spaceId).groupBy { it.category.id }
        val fixed = fixedExpenseRepository.findBySharedSpaceId(spaceId).groupBy { it.category?.id }
        val rows = (sortedCategories(spaceId) + listOf(null)).map { category ->
            val id = category?.id
            val history = months.map { m ->
                val categoryRules = rules[id].orEmpty()
                val override = categoryRules.find { !it.recurring && it.month == m.atDay(1) }
                val recurring = categoryRules.filter { it.recurring && it.month <= m.atDay(1) }.maxByOrNull { it.month }
                com.dong.daytous.dto.CategoryMonthTotal(m.toString(), totals[id to m] ?: java.math.BigDecimal.ZERO, if (override != null) override.amount else recurring?.amount)
            }
            com.dong.daytous.dto.CategoryOverviewRow(category?.toResponse(), history,
                id != null && (budgetEntryRepository.existsByCategoryId(id) || rules[id].orEmpty().isNotEmpty()),
                fixed[id].orEmpty().mapNotNull { it.id })
        }
        return com.dong.daytous.dto.CategoryOverview(monthValue, rows.fold(java.math.BigDecimal.ZERO) { total, row -> total + row.history.last().spent }, rows)
    }

    private fun checkAccess(spaceId: UUID, email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }
        if (user.sharedSpace?.id != spaceId) {
            throw IllegalArgumentException("Access denied: User does not belong to this shared space")
        }
    }
}
