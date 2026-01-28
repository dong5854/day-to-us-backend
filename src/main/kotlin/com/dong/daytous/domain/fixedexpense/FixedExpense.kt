package com.dong.daytous.domain.fixedexpense

import com.dong.daytous.domain.sharedspace.SharedSpace
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Objects
import java.util.UUID

@Entity
@Table(name = "fixed_expense")
class FixedExpense(
    @Column(nullable = false)
    val description: String,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val frequency: Frequency,

    @Column(nullable = false)
    val startDate: LocalDate, // 시작일 (결제일 계산 기준)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_space_id", nullable = false)
    val sharedSpace: SharedSpace,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FixedExpense) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = Objects.hash(id)

    override fun toString(): String = "FixedExpense(id=$id, description='$description', amount=$amount, frequency=$frequency)"
}
