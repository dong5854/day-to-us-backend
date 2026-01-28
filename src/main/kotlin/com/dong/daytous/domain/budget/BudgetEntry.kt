package com.dong.daytous.domain.budget

import com.dong.daytous.domain.sharedspace.SharedSpace
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "budget_entry")
data class BudgetEntry(
    val description: String,
    val amount: Double,
    
    @Column(nullable = false)
    val date: LocalDate, // 지출 날짜

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_space_id")
    var sharedSpace: SharedSpace? = null,

    @Column(name = "fixed_expense_id")
    val fixedExpenseId: UUID? = null,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null
}
