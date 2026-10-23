package com.dong.daytous.repository

import com.dong.daytous.domain.category.CategoryBudget
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface CategoryBudgetRepository : JpaRepository<CategoryBudget, UUID> {
    fun findByCategorySharedSpaceId(spaceId: UUID): List<CategoryBudget>
    fun findByCategoryIdAndMonthAndRecurring(categoryId: UUID, month: LocalDate, recurring: Boolean): CategoryBudget?
    fun existsByCategoryId(categoryId: UUID): Boolean
}
