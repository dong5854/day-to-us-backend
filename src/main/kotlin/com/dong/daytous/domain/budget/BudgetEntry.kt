package com.dong.daytous.domain.budget

import com.dong.daytous.domain.sharedspace.SharedSpace
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@Entity
@Table(name = "budget_entry")
data class BudgetEntry(
    val description: String,
    val amount: Double,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_space_id")
    var sharedSpace: SharedSpace? = null,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null
}
