package com.dong.daytous.domain.category

import com.dong.daytous.domain.sharedspace.SharedSpace
import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.util.Objects
import java.util.UUID

@Entity
@Table(
    name = "expense_category",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["name", "shared_space_id"])
    ]
)
class ExpenseCategory(
    @Column(nullable = false)
    val name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_space_id", nullable = false)
    val sharedSpace: SharedSpace,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExpenseCategory) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = Objects.hash(id)

    override fun toString(): String = "ExpenseCategory(id=$id, name='$name')"
}
