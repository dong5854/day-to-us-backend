package com.dong.daytous.domain.fixedexpense

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "recurring_occurrence", uniqueConstraints = [UniqueConstraint(columnNames = ["fixed_expense_id", "scheduled_date"])])
class RecurringOccurrence(
    @Column(nullable = false) val fixedExpenseId: UUID,
    @Column(nullable = false) val scheduledDate: LocalDate,
    var budgetEntryId: UUID? = null,
    // Also protects legacy entries whose edit history was not recorded.
    @Column(nullable = false) var protectedEntry: Boolean = false,
) {
    @Id @UuidGenerator var id: UUID? = null
}
