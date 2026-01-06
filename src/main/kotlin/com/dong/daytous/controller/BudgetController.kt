package com.dong.daytous.controller

import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.dto.BudgetEntryResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.service.BudgetService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/shared-spaces/{spaceId}/budget-entries")
class BudgetController(
    private val budgetService: BudgetService,
) {
    @GetMapping
    fun getBudgetEntriesForSpace(
        @PathVariable spaceId: UUID,
    ): List<BudgetEntryResponse> =
        budgetService.getAllBudgetEntriesForSpace(spaceId).map { it.toResponse() }

    @GetMapping("/{entryId}")
    fun getBudgetEntryById(
        @PathVariable spaceId: UUID,
        @PathVariable entryId: UUID,
    ): ResponseEntity<BudgetEntryResponse> {
        val entry = budgetService.getBudgetEntryById(spaceId, entryId)
        return ResponseEntity.ok(entry.toResponse())
    }

    @PostMapping
    fun createBudgetEntry(
        @PathVariable spaceId: UUID,
        @RequestBody request: BudgetEntryRequest,
    ): ResponseEntity<BudgetEntryResponse> {
        val createdEntry = budgetService.createBudgetEntry(spaceId, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(createdEntry.toResponse())
    }

    @PutMapping("/{entryId}")
    fun updateBudgetEntry(
        @PathVariable spaceId: UUID,
        @PathVariable entryId: UUID,
        @RequestBody request: BudgetEntryRequest,
    ): ResponseEntity<BudgetEntryResponse> {
        val updatedEntry = budgetService.updateBudgetEntry(spaceId, entryId, request)
        return ResponseEntity.ok(updatedEntry.toResponse())
    }

    @DeleteMapping("/{entryId}")
    fun deleteBudgetEntry(
        @PathVariable spaceId: UUID,
        @PathVariable entryId: UUID,
    ): ResponseEntity<Void> {
        budgetService.deleteBudgetEntry(spaceId, entryId)
        return ResponseEntity.noContent().build()
    }
}
