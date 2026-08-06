package com.dong.daytous.dto

import com.dong.daytous.domain.paymentmethod.PaymentMethod

fun PaymentMethod.toResponse(): PaymentMethodResponse =
    PaymentMethodResponse(
        id = this.id ?: throw IllegalStateException("PaymentMethod ID cannot be null"),
        name = this.name
    )
