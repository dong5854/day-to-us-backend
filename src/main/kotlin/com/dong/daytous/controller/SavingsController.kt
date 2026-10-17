package com.dong.daytous.controller

import com.dong.daytous.dto.*
import com.dong.daytous.service.SavingsService
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/shared-spaces/{spaceId}/savings")
class SavingsController(private val service: SavingsService) {
    @GetMapping
    fun month(@PathVariable spaceId: UUID, @RequestParam year: Int, @RequestParam month: Int, p: Principal) =
        service.month(spaceId, year, month, p.name)
    @PostMapping
    fun create(@PathVariable spaceId: UUID, @RequestBody r: SavingsPlanRequest, p: Principal) = service.create(spaceId, r, p.name)
    @PutMapping("/{id}")
    fun update(@PathVariable spaceId: UUID, @PathVariable id: UUID, @RequestBody r: SavingsPlanRequest, p: Principal) = service.update(spaceId, id, r, p.name)
    @PostMapping("/{id}/preview")
    fun preview(@PathVariable spaceId: UUID, @PathVariable id: UUID, @RequestBody r: SavingsPlanRequest, p: Principal) = service.preview(spaceId, id, r, p.name)
    @DeleteMapping("/{id}")
    fun archive(@PathVariable spaceId: UUID, @PathVariable id: UUID, p: Principal) = service.archive(spaceId, id, p.name)
    @PostMapping("/{id}/records")
    fun record(@PathVariable spaceId: UUID, @PathVariable id: UUID, @RequestBody r: SavingsRecordRequest, p: Principal) = service.createRecord(spaceId, id, r, p.name)
    @PutMapping("/{id}/records/{recordId}")
    fun updateRecord(@PathVariable spaceId: UUID, @PathVariable id: UUID, @PathVariable recordId: UUID, @RequestBody r: SavingsRecordRequest, p: Principal) = service.updateRecord(spaceId, id, recordId, r, p.name)
    @DeleteMapping("/{id}/records/{recordId}")
    fun deleteRecord(@PathVariable spaceId: UUID, @PathVariable id: UUID, @PathVariable recordId: UUID, p: Principal) = service.deleteRecord(spaceId, id, recordId, p.name)
}
