package com.dong.daytous.service

import com.dong.daytous.domain.push.PushSubscription
import com.dong.daytous.dto.PushSubscribeRequest
import com.dong.daytous.dto.PushSubscriptionResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.PushSubscriptionRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PushSubscriptionService(
    private val pushSubscriptionRepository: PushSubscriptionRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun subscribe(
        email: String,
        request: PushSubscribeRequest,
    ): PushSubscriptionResponse {
        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { EntityNotFoundException("User not found") }

        val existing = pushSubscriptionRepository.findByUserIdAndEndpoint(user.id, request.endpoint)
        if (existing != null) {
            val updated =
                PushSubscription(
                    user = user,
                    endpoint = request.endpoint,
                    p256dh = request.keys.p256dh,
                    auth = request.keys.auth,
                ).apply {
                    id = existing.id
                }
            return pushSubscriptionRepository.save(updated).toResponse()
        }

        val subscription =
            PushSubscription(
                user = user,
                endpoint = request.endpoint,
                p256dh = request.keys.p256dh,
                auth = request.keys.auth,
            )
        return pushSubscriptionRepository.save(subscription).toResponse()
    }

    @Transactional
    fun unsubscribe(
        email: String,
        endpoint: String,
    ) {
        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { EntityNotFoundException("User not found") }

        pushSubscriptionRepository.deleteByUserIdAndEndpoint(user.id, endpoint)
    }
}
