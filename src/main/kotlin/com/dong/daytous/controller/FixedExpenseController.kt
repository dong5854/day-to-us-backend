package com.dong.daytous.controller

import com.dong.daytous.dto.FixedExpenseRequest
import com.dong.daytous.dto.FixedExpenseResponse
import com.dong.daytous.service.FixedExpenseService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/shared-spaces/{spaceId}/fixed-expenses")
class FixedExpenseController(
    private val fixedExpenseService: FixedExpenseService,
) {
    @PostMapping
    fun createFixedExpense(
        @PathVariable spaceId: UUID,
        @Valid @RequestBody request: FixedExpenseRequest,
        principal: Principal,
    ): ResponseEntity<FixedExpenseResponse> {
        val createdExpense = fixedExpenseService.createFixedExpense(spaceId, request, principal.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdExpense)
    }

    @GetMapping
    fun getAllFixedExpenses(
        @PathVariable spaceId: UUID,
        principal: Principal,
    ): List<FixedExpenseResponse> = fixedExpenseService.getAllFixedExpenses(spaceId, principal.name)
}
