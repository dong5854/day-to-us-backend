package com.dong.daytous.domain.category

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "category_budget", uniqueConstraints = [UniqueConstraint(columnNames = ["category_id", "budget_month", "recurring"])])
class CategoryBudget(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id", nullable = false)
    val category: ExpenseCategory,
    @Column(name = "budget_month", nullable = false) val month: LocalDate,
    @Column(nullable = false) val recurring: Boolean,
    var amount: Long?,
) {
    @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null
}
