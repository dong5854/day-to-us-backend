package com.dong.daytous.service

import com.dong.daytous.domain.push.PushSubscription
import com.dong.daytous.repository.PushSubscriptionRepository
import jakarta.annotation.PostConstruct
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.Security

@Service
class WebPushService(
    @Value("\${vapid.public-key:}") private val publicKey: String,
    @Value("\${vapid.private-key:}") private val privateKey: String,
    private val pushSubscriptionRepository: PushSubscriptionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var pushService: PushService? = null

    @PostConstruct
    fun init() {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.warn("VAPID keys not configured. Web push notifications are disabled.")
            return
        }
        Security.addProvider(BouncyCastleProvider())
        pushService = PushService(publicKey, privateKey, "mailto:noreply@daytous.com")
    }

    fun isEnabled(): Boolean = pushService != null

    fun sendNotification(
        subscription: PushSubscription,
        title: String,
        body: String,
        url: String? = "/",
        tag: String? = null,
    ) {
        val service = pushService ?: return

        val payload = buildPayload(title, body, url, tag)
        val notification =
            Notification(
                subscription.endpoint,
                subscription.p256dh,
                subscription.auth,
                payload,
            )

        try {
            val response = service.send(notification)
            val statusCode = response.statusLine.statusCode
            if (statusCode in listOf(404, 410)) {
                log.info("Push subscription expired ({}), removing: {}", statusCode, subscription.endpoint)
                pushSubscriptionRepository.delete(subscription)
            }
        } catch (e: Exception) {
            log.warn("Failed to send push notification to {}: {}", subscription.endpoint, e.message)
        }
    }

    private fun buildPayload(
        title: String,
        body: String,
        url: String?,
        tag: String?,
    ): String {
        val parts = mutableListOf(
            """"title":"$title"""",
            """"body":"$body"""",
        )
        url?.let { parts.add(""""url":"$it"""") }
        tag?.let { parts.add(""""tag":"$it"""") }
        parts.add(""""timestamp":${System.currentTimeMillis()}""")
        return "{${parts.joinToString(",")}}"
    }
}
