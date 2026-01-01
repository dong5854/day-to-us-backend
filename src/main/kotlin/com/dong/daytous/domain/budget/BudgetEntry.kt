package com.dong.daytous.domain.budget

import com.dong.daytous.domain.sharedspace.SharedSpace
import jakarta.persistence.*

@Entity
@Table(name = "budget_entry")
data class BudgetEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val description: String,
    val amount: Double,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_space_id")
    var sharedSpace: SharedSpace? = null
)
