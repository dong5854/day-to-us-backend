package com.dong.daytous.service

import com.dong.daytous.domain.fixedexpense.FixedExpense
import com.dong.daytous.dto.FixedExpenseRequest
import com.dong.daytous.dto.FixedExpenseResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.ExpenseCategoryRepository
import com.dong.daytous.repository.FixedExpenseRepository
import com.dong.daytous.repository.PaymentMethodRepository
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
    private val expenseCategoryRepository: ExpenseCategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
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

        val category = request.categoryId?.let {
            expenseCategoryRepository.findById(it)
                .filter { cat -> cat.sharedSpace.id == spaceId }
                .orElseThrow { EntityNotFoundException("Category not found or does not belong to this space") }
        }

        val paymentMethod = request.paymentMethodId?.let {
            paymentMethodRepository.findById(it)
                .filter { pm -> pm.sharedSpace.id == spaceId }
                .orElseThrow { EntityNotFoundException("PaymentMethod not found or does not belong to this space") }
        }

        val newExpense =
            FixedExpense(
                description = request.description,
                amount = request.amount,
                frequency = request.frequency,
                startDate = request.startDate,
                category = category,
                paymentMethod = paymentMethod,
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

    @Transactional
    fun updateFixedExpense(
        spaceId: UUID,
        expenseId: UUID,
        request: FixedExpenseRequest,
        email: String,
    ): FixedExpenseResponse {
        checkAccess(spaceId, email)

        val expense =
            fixedExpenseRepository
                .findById(expenseId)
                .filter { it.sharedSpace.id == spaceId }
                .orElseThrow { EntityNotFoundException("FixedExpense with id $expenseId not found") }

        val category = request.categoryId?.let {
            expenseCategoryRepository.findById(it)
                .filter { cat -> cat.sharedSpace.id == spaceId }
                .orElseThrow { EntityNotFoundException("Category not found or does not belong to this space") }
        }

        val paymentMethod = request.paymentMethodId?.let {
            paymentMethodRepository.findById(it)
                .filter { pm -> pm.sharedSpace.id == spaceId }
                .orElseThrow { EntityNotFoundException("PaymentMethod not found or does not belong to this space") }
        }

        val updatedExpense =
            FixedExpense(
                description = request.description,
                amount = request.amount,
                frequency = request.frequency,
                startDate = request.startDate,
                category = category,
                paymentMethod = paymentMethod,
                sharedSpace = expense.sharedSpace,
            ).apply {
                id = expense.id
            }

        return fixedExpenseRepository.save(updatedExpense).toResponse()
    }

    @Transactional
    fun deleteFixedExpense(
        spaceId: UUID,
        expenseId: UUID,
        email: String,
    ) {
        checkAccess(spaceId, email)

        val expense =
            fixedExpenseRepository
                .findById(expenseId)
                .filter { it.sharedSpace.id == spaceId }
                .orElseThrow { EntityNotFoundException("FixedExpense with id $expenseId not found") }

        fixedExpenseRepository.delete(expense)
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
