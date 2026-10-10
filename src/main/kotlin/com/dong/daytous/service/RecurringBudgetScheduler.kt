package com.dong.daytous.service

import com.dong.daytous.repository.FixedExpenseRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
@ConditionalOnProperty(name = ["recurring.auto-post.enabled"], havingValue = "true", matchIfMissing = true)
class RecurringBudgetScheduler(
    private val repository: FixedExpenseRepository,
    private val service: RecurringBudgetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    fun postDueEntries() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        repository.findAllIds().forEach { id ->
            try {
                service.postDueEntries(id, today)
            } catch (error: Exception) {
                log.error("Failed to post recurring entry {}", id, error)
            }
        }
    }
}
