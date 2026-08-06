package com.dong.daytous.repository

import com.dong.daytous.domain.paymentmethod.PaymentMethod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PaymentMethodRepository : JpaRepository<PaymentMethod, UUID> {
    fun findBySharedSpaceId(spaceId: UUID): List<PaymentMethod>
    fun findByIdAndSharedSpaceId(id: UUID, spaceId: UUID): PaymentMethod?
}
