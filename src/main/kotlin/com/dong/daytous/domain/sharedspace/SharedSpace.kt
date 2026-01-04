package com.dong.daytous.domain.sharedspace

import com.dong.daytous.domain.budget.BudgetEntry
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.Objects
import java.util.UUID

@Entity
@Table(name = "shared_space")
class SharedSpace(
    @Column(nullable = false)
    val name: String,
    @OneToMany(mappedBy = "sharedSpace", cascade = [CascadeType.ALL], orphanRemoval = true)
    val budgetEntries: MutableList<BudgetEntry> = mutableListOf(),
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SharedSpace) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = Objects.hash(id)

    override fun toString(): String = "SharedSpace(id=$id, name='$name')"
}
