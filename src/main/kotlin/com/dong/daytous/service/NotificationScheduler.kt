package com.dong.daytous.service

import com.dong.daytous.domain.fixedexpense.FixedExpense
import com.dong.daytous.domain.fixedexpense.Frequency
import com.dong.daytous.repository.FixedExpenseRepository
import com.dong.daytous.repository.PushSubscriptionRepository
import com.dong.daytous.repository.ScheduleRepository
import com.dong.daytous.repository.SharedSpaceRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class NotificationScheduler(
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val sharedSpaceRepository: SharedSpaceRepository,
    private val pushSubscriptionRepository: PushSubscriptionRepository,
    private val webPushService: WebPushService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    fun sendDailyNotifications() {
        if (!webPushService.isEnabled()) {
            log.debug("Web push is disabled, skipping notifications")
            return
        }

        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val tomorrow = today.plusDays(1)

        sendFixedExpenseNotifications(today, tomorrow)
        sendScheduleNotifications(today, tomorrow)
    }

    private fun sendFixedExpenseNotifications(today: LocalDate, tomorrow: LocalDate) {
        val allSpaces = sharedSpaceRepository.findAll()

        for (space in allSpaces) {
            val expenses = fixedExpenseRepository.findBySharedSpaceId(space.id!!)
            val subscriptions = pushSubscriptionRepository.findByUserSharedSpaceId(space.id!!)
            if (subscriptions.isEmpty()) continue

            for (expense in expenses) {
                val nextPaymentDate = calculateNextPaymentDate(expense, today)

                when (nextPaymentDate) {
                    today -> {
                        val title = "\uD83D\uDCB3 고정지출 결제일"
                        val body = "${expense.description} ₩${expense.amount.toPlainString()} 결제일입니다"
                        subscriptions.forEach { sub ->
                            webPushService.sendNotification(sub, title, body, "/", "fixed-expense-${expense.id}")
                        }
                    }
                    tomorrow -> {
                        val title = "\uD83D\uDCB3 내일 고정지출 결제"
                        val body = "내일 ${expense.description} ₩${expense.amount.toPlainString()} 결제 예정입니다"
                        subscriptions.forEach { sub ->
                            webPushService.sendNotification(sub, title, body, "/", "fixed-expense-${expense.id}")
                        }
                    }
                }
            }
        }
    }

    private fun sendScheduleNotifications(today: LocalDate, tomorrow: LocalDate) {
        val todayStart = LocalDateTime.of(today, LocalTime.MIN)
        val todayEnd = LocalDateTime.of(today, LocalTime.MAX)
        val tomorrowStart = LocalDateTime.of(tomorrow, LocalTime.MIN)
        val tomorrowEnd = LocalDateTime.of(tomorrow, LocalTime.MAX)

        val todaySchedules = scheduleRepository.findByStartDateTimeBetween(todayStart, todayEnd)
        for (schedule in todaySchedules) {
            val subscriptions = pushSubscriptionRepository.findByUserSharedSpaceId(schedule.sharedSpace.id!!)
            if (subscriptions.isEmpty()) continue

            val title = "\uD83D\uDCC5 오늘 일정"
            val time = schedule.startDateTime.format(timeFormatter)
            val body = "${schedule.title} ($time)"
            subscriptions.forEach { sub ->
                webPushService.sendNotification(sub, title, body, "/", "schedule-${schedule.id}")
            }
        }

        val tomorrowSchedules = scheduleRepository.findByStartDateTimeBetween(tomorrowStart, tomorrowEnd)
        for (schedule in tomorrowSchedules) {
            val subscriptions = pushSubscriptionRepository.findByUserSharedSpaceId(schedule.sharedSpace.id!!)
            if (subscriptions.isEmpty()) continue

            val title = "\uD83D\uDCC5 내일 일정"
            val time = schedule.startDateTime.format(timeFormatter)
            val body = "내일 ${schedule.title} ($time) 예정입니다"
            subscriptions.forEach { sub ->
                webPushService.sendNotification(sub, title, body, "/", "schedule-${schedule.id}")
            }
        }
    }

    companion object {
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
                    val monthsBetween = java.time.Period.between(date, today).toTotalMonths()
                    date = date.plusMonths(monthsBetween)
                    while (date < today) {
                        date = date.plusMonths(1)
                    }
                }
                Frequency.YEARLY -> {
                    val yearsBetween = java.time.Period.between(date, today).years.toLong()
                    date = date.plusYears(yearsBetween)
                    while (date < today) {
                        date = date.plusYears(1)
                    }
                }
            }
            return date
        }
    }
}
