package com.dong.daytous.controller

import com.dong.daytous.dto.BudgetEntryResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class BudgetController {
    @GetMapping("/budget-entries")
    fun getBudgetEntries(): List<BudgetEntryResponse> =
        listOf(
            BudgetEntryResponse(1, "커피", 4500.0),
            BudgetEntryResponse(2, "점심 식사", 12000.0),
            BudgetEntryResponse(3, "장보기", 35000.0),
        )
}
