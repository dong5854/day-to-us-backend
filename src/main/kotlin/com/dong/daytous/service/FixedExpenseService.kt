package com.dong.daytous.service

import com.dong.daytous.domain.fixedexpense.FixedExpense
import com.dong.daytous.dto.FixedExpenseRequest
import com.dong.daytous.dto.FixedExpenseResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.FixedExpenseRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FixedExpenseService(
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val sharedSpaceRepository: SharedSpaceRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createFixedExpense(
        spaceId: UUID,
        request: FixedExpenseRequest,
        email: String,
    ): FixedExpenseResponse {
        checkAccess(spaceId, email)

        val sharedSpace =
            sharedSpaceRepository
                .findById(spaceId)
                .orElseThrow { EntityNotFoundException("SharedSpace with id $spaceId not found") }

        val newExpense =
            FixedExpense(
                description = request.description,
                amount = request.amount,
                frequency = request.frequency,
                startDate = request.startDate,
                sharedSpace = sharedSpace,
            )

        val savedExpense = fixedExpenseRepository.save(newExpense)
        return savedExpense.toResponse()
    }

    @Transactional(readOnly = true)
    fun getAllFixedExpenses(
        spaceId: UUID,
        email: String,
    ): List<FixedExpenseResponse> {
        checkAccess(spaceId, email)
        return fixedExpenseRepository.findBySharedSpaceId(spaceId).map { it.toResponse() }
    }

    private fun checkAccess(
        spaceId: UUID,
        email: String,
    ) {
        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { EntityNotFoundException("User not found") }

        if (user.sharedSpace?.id != spaceId) {
            throw IllegalArgumentException("Access denied: User does not belong to this shared space")
        }
    }
}
