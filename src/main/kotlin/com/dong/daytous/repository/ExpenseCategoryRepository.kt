package com.dong.daytous.repository

import com.dong.daytous.domain.category.ExpenseCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExpenseCategoryRepository : JpaRepository<ExpenseCategory, UUID> {
    fun findBySharedSpaceId(spaceId: UUID): List<ExpenseCategory>
    fun findByIdAndSharedSpaceId(id: UUID, spaceId: UUID): ExpenseCategory?
}
