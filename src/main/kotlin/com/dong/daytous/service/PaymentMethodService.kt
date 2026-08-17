package com.dong.daytous.service

import com.dong.daytous.domain.paymentmethod.PaymentMethod
import com.dong.daytous.dto.PaymentMethodRequest
import com.dong.daytous.dto.PaymentMethodResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.PaymentMethodRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PaymentMethodService(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val sharedSpaceRepository: SharedSpaceRepository,
    private val userRepository: UserRepository,
    private val budgetEntryRepository: BudgetEntryRepository,
) {
    @Transactional
    fun createPaymentMethod(
        spaceId: UUID,
        request: PaymentMethodRequest,
        email: String,
    ): PaymentMethodResponse {
        checkAccess(spaceId, email)
        val sharedSpace = sharedSpaceRepository.findById(spaceId)
            .orElseThrow { EntityNotFoundException("SharedSpace with id $spaceId not found") }
        val method = PaymentMethod(name = request.name, sharedSpace = sharedSpace)
        return paymentMethodRepository.save(method).toResponse()
    }

    @Transactional(readOnly = true)
    fun getAllPaymentMethods(
        spaceId: UUID,
        email: String,
    ): List<PaymentMethodResponse> {
        checkAccess(spaceId, email)
        return paymentMethodRepository.findBySharedSpaceId(spaceId).map { it.toResponse() }
    }

    @Transactional
    fun deletePaymentMethod(
        spaceId: UUID,
        methodId: UUID,
        email: String,
    ) {
        checkAccess(spaceId, email)
        val method = paymentMethodRepository.findByIdAndSharedSpaceId(methodId, spaceId)
            ?: throw EntityNotFoundException("PaymentMethod with id $methodId not found in space $spaceId")
        // 기존 가계부 항목의 결제수단 참조를 NULL로 처리 후 삭제
        budgetEntryRepository.nullifyPaymentMethodById(methodId)
        paymentMethodRepository.delete(method)
    }

    private fun checkAccess(spaceId: UUID, email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }
        if (user.sharedSpace?.id != spaceId) {
            throw IllegalArgumentException("Access denied: User does not belong to this shared space")
        }
    }
}
