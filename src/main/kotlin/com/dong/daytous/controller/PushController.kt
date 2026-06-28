package com.dong.daytous.controller

import com.dong.daytous.dto.PushSubscribeRequest
import com.dong.daytous.dto.PushSubscriptionResponse
import com.dong.daytous.dto.PushUnsubscribeRequest
import com.dong.daytous.dto.VapidKeyResponse
import com.dong.daytous.service.PushSubscriptionService
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/push")
class PushController(
    private val pushSubscriptionService: PushSubscriptionService,
    @Value("\${vapid.public-key:}") private val vapidPublicKey: String,
) {
    @GetMapping("/vapid-key")
    fun getVapidKey(): ResponseEntity<VapidKeyResponse> =
        ResponseEntity.ok(VapidKeyResponse(publicKey = vapidPublicKey))

    @PostMapping("/subscriptions")
    fun subscribe(
        @Valid @RequestBody request: PushSubscribeRequest,
        principal: Principal,
    ): ResponseEntity<PushSubscriptionResponse> {
        val response = pushSubscriptionService.subscribe(principal.name, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/subscriptions")
    fun unsubscribe(
        @Valid @RequestBody request: PushUnsubscribeRequest,
        principal: Principal,
    ): ResponseEntity<Void> {
        pushSubscriptionService.unsubscribe(principal.name, request.endpoint)
        return ResponseEntity.noContent().build()
    }
}
