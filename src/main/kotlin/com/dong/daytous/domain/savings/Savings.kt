package com.dong.daytous.domain.savings

import com.dong.daytous.domain.sharedspace.SharedSpace
import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "savings_plan")
class SavingsPlan(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    val sharedSpace: SharedSpace,
    var name: String,
    var monthlyAmount: Long? = null,
    var firstDate: LocalDate? = null,
    var autoPostFrom: LocalDate? = null,
    var maturityDate: LocalDate? = null,
    var targetAmount: Long? = null,
    var payerEmail: String,
    var payerName: String,
    @Column(length = 1000) var memo: String = "",
    var archived: Boolean = false,
) {
    @Id @UuidGenerator var id: UUID? = null
    @Version var version: Long = 0
}

@Entity
@Table(name = "savings_record", uniqueConstraints = [UniqueConstraint(columnNames = ["plan_id", "occurrence_month"])])
class SavingsRecord(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "plan_id", nullable = false)
    val plan: SavingsPlan,
    var date: LocalDate,
    var amount: Long,
    var payerEmail: String,
    var payerName: String,
    @Column(length = 1000) var memo: String = "",
    // A month identifies a scheduled installment even after its date is edited.
    @Column(name = "occurrence_month") val occurrenceMonth: LocalDate? = null,
    var edited: Boolean = false,
    var deleted: Boolean = false,
) {
    @Id @UuidGenerator var id: UUID? = null
    @Version var version: Long = 0
}
