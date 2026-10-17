package com.dong.daytous.service

import com.dong.daytous.repository.SavingsPlanRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
@ConditionalOnProperty(name = ["savings.auto-post.enabled"], havingValue = "true", matchIfMissing = true)
class SavingsScheduler(private val plans: SavingsPlanRepository, private val service: SavingsService) {
    private val log = LoggerFactory.getLogger(javaClass)
    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    fun post() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        plans.scheduledIds().forEach { id ->
            try { service.postDue(id, today) }
            catch (e: Exception) { log.error("Savings posting failed for {}", id, e) }
        }
    }
}
