package com.dong.daytous.service

import com.dong.daytous.repository.KisConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class KisScheduler(private val connections: KisConnectionRepository, private val service: KisService) {
    private val log = LoggerFactory.getLogger(javaClass)
    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    fun sync() {
        connections.findAll().forEach { c ->
            try { service.syncDue(c.planId) }
            catch (_: Exception) { log.warn("Pension sync failed for connection {}", c.planId) }
        }
    }
}
