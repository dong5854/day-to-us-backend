package com.dong.daytous.repository

import com.dong.daytous.domain.budget.BudgetEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BudgetEntryRepository : JpaRepository<BudgetEntry, UUID> {
    fun findBySharedSpaceId(spaceId: UUID): List<BudgetEntry>

    fun findBySharedSpaceIdAndDateBetween(
        spaceId: UUID,
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate,
    ): List<BudgetEntry>

    @Modifying
    @Query("UPDATE BudgetEntry b SET b.category = null WHERE b.category.id = :categoryId")
    fun nullifyCategoryById(categoryId: UUID)

    @Modifying
    @Query("UPDATE BudgetEntry b SET b.paymentMethod = null WHERE b.paymentMethod.id = :methodId")
    fun nullifyPaymentMethodById(methodId: UUID)
}
