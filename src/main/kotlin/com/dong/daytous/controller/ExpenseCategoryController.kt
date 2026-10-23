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
        @RequestParam(defaultValue = "false") includeArchived: Boolean = false,
    ): List<ExpenseCategoryResponse> =
        if (includeArchived) expenseCategoryService.getIncludingArchived(spaceId, principal.name)
        else expenseCategoryService.getAllCategories(spaceId, principal.name)

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
    @PatchMapping("/{id}")
    fun rename(@PathVariable spaceId: UUID, @PathVariable id: UUID, @RequestBody request: ExpenseCategoryRequest, principal: Principal) =
        expenseCategoryService.rename(spaceId, id, request, principal.name)

    @PutMapping("/order")
    fun reorder(@PathVariable spaceId: UUID, @RequestBody request: com.dong.daytous.dto.CategoryOrderRequest, principal: Principal) =
        expenseCategoryService.reorder(spaceId, request.ids, principal.name)

    @PutMapping("/{id}/archive")
    fun archive(@PathVariable spaceId: UUID, @PathVariable id: UUID, @RequestBody request: com.dong.daytous.dto.CategoryArchiveRequest, principal: Principal) =
        expenseCategoryService.archive(spaceId, id, request.archived, principal.name)

    @PutMapping("/{id}/budgets/{month}")
    fun budget(@PathVariable spaceId: UUID, @PathVariable id: UUID, @PathVariable month: String, @RequestBody request: com.dong.daytous.dto.CategoryBudgetRequest, principal: Principal) =
        expenseCategoryService.setBudget(spaceId, id, month, request, principal.name)

    @GetMapping("/overview")
    fun overview(@PathVariable spaceId: UUID, @RequestParam month: String, principal: Principal) =
        expenseCategoryService.overview(spaceId, month, principal.name)
}
