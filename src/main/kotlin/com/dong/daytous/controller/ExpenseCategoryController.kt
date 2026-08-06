package com.dong.daytous.controller

import com.dong.daytous.dto.ExpenseCategoryRequest
import com.dong.daytous.dto.ExpenseCategoryResponse
import com.dong.daytous.service.ExpenseCategoryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/shared-spaces/{spaceId}/expense-categories")
class ExpenseCategoryController(
    private val expenseCategoryService: ExpenseCategoryService,
) {
    @GetMapping
    fun getAllCategories(
        @PathVariable spaceId: UUID,
        principal: Principal,
    ): List<ExpenseCategoryResponse> =
        expenseCategoryService.getAllCategories(spaceId, principal.name)

    @PostMapping
    fun createCategory(
        @PathVariable spaceId: UUID,
        @RequestBody request: ExpenseCategoryRequest,
        principal: Principal,
    ): ResponseEntity<ExpenseCategoryResponse> {
        val created = expenseCategoryService.createCategory(spaceId, request, principal.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @DeleteMapping("/{categoryId}")
    fun deleteCategory(
        @PathVariable spaceId: UUID,
        @PathVariable categoryId: UUID,
        principal: Principal,
    ): ResponseEntity<Void> {
        expenseCategoryService.deleteCategory(spaceId, categoryId, principal.name)
        return ResponseEntity.noContent().build()
    }
}
