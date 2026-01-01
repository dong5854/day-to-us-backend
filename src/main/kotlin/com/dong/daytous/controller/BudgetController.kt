package com.dong.daytous.controller

import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.dto.BudgetEntryResponse
import com.dong.daytous.service.BudgetService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/shared-spaces/{spaceId}/budget-entries")
class BudgetController(
    private val budgetService: BudgetService
) {
    @GetMapping
    fun getBudgetEntriesForSpace(@PathVariable spaceId: Long): List<BudgetEntryResponse> =
        budgetService.getAllBudgetEntriesForSpace(spaceId).map { entry ->
            BudgetEntryResponse(entry.id, entry.description, entry.amount)
        }

    @GetMapping("/{entryId}")
    fun getBudgetEntryById(
        @PathVariable spaceId: Long,
        @PathVariable entryId: Long
    ): ResponseEntity<BudgetEntryResponse> {
        val entry = budgetService.getBudgetEntryById(spaceId, entryId)
        return entry?.let {
            ResponseEntity.ok(BudgetEntryResponse(it.id, it.description, it.amount))
        } ?: ResponseEntity.notFound().build()
    }

    @PostMapping
    fun createBudgetEntry(
        @PathVariable spaceId: Long,
        @RequestBody request: BudgetEntryRequest
    ): ResponseEntity<BudgetEntryResponse> {
        val createdEntry = budgetService.createBudgetEntry(spaceId, request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(BudgetEntryResponse(createdEntry.id, createdEntry.description, createdEntry.amount))
    }

    @PutMapping("/{entryId}")
    fun updateBudgetEntry(
        @PathVariable spaceId: Long,
        @PathVariable entryId: Long,
        @RequestBody request: BudgetEntryRequest
    ): ResponseEntity<BudgetEntryResponse> {
        val updatedEntry = budgetService.updateBudgetEntry(spaceId, entryId, request)
        return updatedEntry?.let {
            ResponseEntity.ok(BudgetEntryResponse(it.id, it.description, it.amount))
        } ?: ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{entryId}")
    fun deleteBudgetEntry(
        @PathVariable spaceId: Long,
        @PathVariable entryId: Long
    ): ResponseEntity<Void> {
        val success = budgetService.deleteBudgetEntry(spaceId, entryId)
        return if (success) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
