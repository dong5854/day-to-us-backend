package com.dong.daytous.repository

import com.dong.daytous.domain.category.ExpenseCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExpenseCategoryRepository : JpaRepository<ExpenseCategory, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ExpenseCategory c where c.id = :id and c.sharedSpace.id = :spaceId")
    fun findLocked(id: UUID, spaceId: UUID): ExpenseCategory?
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    override fun findById(id: UUID): java.util.Optional<ExpenseCategory>
    fun findBySharedSpaceId(spaceId: UUID): List<ExpenseCategory>
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    fun findByIdAndSharedSpaceId(id: UUID, spaceId: UUID): ExpenseCategory?
    fun existsByNameAndSharedSpaceId(name: String, spaceId: UUID): Boolean
}
