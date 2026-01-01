package com.dong.daytous.controller

import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.dto.BudgetEntryResponse
import com.dong.daytous.service.BudgetService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class BudgetController(
    private val budgetService: BudgetService,
) {
    @GetMapping("/budget-entries")
    fun getBudgetEntries(): List<BudgetEntryResponse> =
        budgetService.getAllBudgetEntries().map { entry ->
            BudgetEntryResponse(entry.id, entry.description, entry.amount)
        }

    @GetMapping("/budget-entries/{id}")
    fun getBudgetEntryById(
        @PathVariable id: Long,
    ): ResponseEntity<BudgetEntryResponse> {
        val entry = budgetService.getBudgetEntryById(id)
        return entry?.let {
            ResponseEntity.ok(BudgetEntryResponse(it.id, it.description, it.amount))
        } ?: ResponseEntity.notFound().build()
    }

    @PostMapping("/budget-entries")
    fun createBudgetEntry(
        @RequestBody request: BudgetEntryRequest,
    ): ResponseEntity<BudgetEntryResponse> {
        val createdEntry = budgetService.createBudgetEntry(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(BudgetEntryResponse(createdEntry.id, createdEntry.description, createdEntry.amount))
    }

    @PutMapping("/budget-entries/{id}")
    fun updateBudgetEntry(
        @PathVariable id: Long,
        @RequestBody request: BudgetEntryRequest,
    ): ResponseEntity<BudgetEntryResponse> {
        val updatedEntry = budgetService.updateBudgetEntry(id, request)
        return updatedEntry?.let {
            ResponseEntity.ok(BudgetEntryResponse(it.id, it.description, it.amount))
        } ?: ResponseEntity.notFound().build()
    }

    @DeleteMapping("/budget-entries/{id}")
    fun deleteBudgetEntry(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val entry = budgetService.getBudgetEntryById(id)
        return if (entry != null) {
            budgetService.deleteBudgetEntry(id)
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
