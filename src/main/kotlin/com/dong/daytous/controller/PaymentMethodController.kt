package com.dong.daytous.controller

import com.dong.daytous.dto.PaymentMethodRequest
import com.dong.daytous.dto.PaymentMethodResponse
import com.dong.daytous.service.PaymentMethodService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/shared-spaces/{spaceId}/payment-methods")
class PaymentMethodController(
    private val paymentMethodService: PaymentMethodService,
) {
    @GetMapping
    fun getAllPaymentMethods(
        @PathVariable spaceId: UUID,
        principal: Principal,
    ): List<PaymentMethodResponse> =
        paymentMethodService.getAllPaymentMethods(spaceId, principal.name)

    @PostMapping
    fun createPaymentMethod(
        @PathVariable spaceId: UUID,
        @RequestBody request: PaymentMethodRequest,
        principal: Principal,
    ): ResponseEntity<PaymentMethodResponse> {
        val created = paymentMethodService.createPaymentMethod(spaceId, request, principal.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @DeleteMapping("/{methodId}")
    fun deletePaymentMethod(
        @PathVariable spaceId: UUID,
        @PathVariable methodId: UUID,
        principal: Principal,
    ): ResponseEntity<Void> {
        paymentMethodService.deletePaymentMethod(spaceId, methodId, principal.name)
        return ResponseEntity.noContent().build()
    }
}
