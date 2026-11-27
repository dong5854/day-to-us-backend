package com.dong.daytous.domain.fixedexpense

import java.time.LocalDate

fun calculateNextPaymentDate(expense: FixedExpense, today: LocalDate): LocalDate {
    var date = expense.startDate
    if (date >= today) return date

    when (expense.frequency) {
        Frequency.WEEKLY -> {
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(date, today)
            val weeksPassed = daysBetween / 7
            date = date.plusWeeks(weeksPassed)
            while (date < today) {
                date = date.plusWeeks(1)
            }
        }
        Frequency.MONTHLY -> {
            var monthsBetween = java.time.Period.between(date, today).toTotalMonths()
            date = date.plusMonths(monthsBetween)
            while (date < today) {
                monthsBetween++
                date = expense.startDate.plusMonths(monthsBetween)
            }
        }
        Frequency.YEARLY -> {
            var yearsBetween = java.time.Period.between(date, today).years.toLong()
            date = date.plusYears(yearsBetween)
            while (date < today) {
                yearsBetween++
                date = expense.startDate.plusYears(yearsBetween)
            }
        }
    }
    return date
}
