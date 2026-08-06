package com.dong.daytous.service

import com.dong.daytous.domain.category.ExpenseCategory
import com.dong.daytous.dto.ExpenseCategoryRequest
import com.dong.daytous.dto.ExpenseCategoryResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.ExpenseCategoryRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ExpenseCategoryService(
    private val expenseCategoryRepository: ExpenseCategoryRepository,
    private val sharedSpaceRepository: SharedSpaceRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createCategory(
        spaceId: UUID,
        request: ExpenseCategoryRequest,
        email: String,
    ): ExpenseCategoryResponse {
        checkAccess(spaceId, email)
        val sharedSpace = sharedSpaceRepository.findById(spaceId)
            .orElseThrow { EntityNotFoundException("SharedSpace with id $spaceId not found") }
        val category = ExpenseCategory(name = request.name, sharedSpace = sharedSpace)
        return expenseCategoryRepository.save(category).toResponse()
    }

    @Transactional(readOnly = true)
    fun getAllCategories(
        spaceId: UUID,
        email: String,
    ): List<ExpenseCategoryResponse> {
        checkAccess(spaceId, email)
        return expenseCategoryRepository.findBySharedSpaceId(spaceId).map { it.toResponse() }
    }

    @Transactional
    fun deleteCategory(
        spaceId: UUID,
        categoryId: UUID,
        email: String,
    ) {
        checkAccess(spaceId, email)
        val category = expenseCategoryRepository.findByIdAndSharedSpaceId(categoryId, spaceId)
            ?: throw EntityNotFoundException("ExpenseCategory with id $categoryId not found in space $spaceId")
        expenseCategoryRepository.delete(category)
    }

    private fun checkAccess(spaceId: UUID, email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }
        if (user.sharedSpace?.id != spaceId) {
            throw IllegalArgumentException("Access denied: User does not belong to this shared space")
        }
    }
}
