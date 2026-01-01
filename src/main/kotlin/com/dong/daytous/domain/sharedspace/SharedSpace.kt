package com.dong.daytous.domain.sharedspace

import com.dong.daytous.domain.budget.BudgetEntry
import jakarta.persistence.*
import java.util.Objects

@Entity
@Table(name = "shared_space")
class SharedSpace(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val name: String,

    @OneToMany(mappedBy = "sharedSpace", cascade = [CascadeType.ALL], orphanRemoval = true)
    val budgetEntries: MutableList<BudgetEntry> = mutableListOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SharedSpace) return false
        if (id == 0L || other.id == 0L) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    override fun toString(): String {
        return "SharedSpace(id=$id, name='$name')"
    }
}
