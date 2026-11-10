package com.dong.daytous.controller

import com.dong.daytous.dto.KisConnectRequest
import com.dong.daytous.service.KisService
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/shared-spaces/{spaceId}/pension-connections")
class KisController(private val service: KisService) {
    @GetMapping fun status(@PathVariable spaceId: UUID, p: Principal) = service.status(spaceId, p.name)
    @PostMapping("/{id}") fun connect(@PathVariable spaceId: UUID, @PathVariable id: UUID, @RequestBody r: KisConnectRequest, p: Principal) = service.connect(spaceId, id, r, p.name)
    @PostMapping("/{id}/sync") fun sync(@PathVariable spaceId: UUID, @PathVariable id: UUID, p: Principal) = service.sync(spaceId, id, p.name)
    @DeleteMapping("/{id}") fun disconnect(@PathVariable spaceId: UUID, @PathVariable id: UUID, p: Principal) = service.disconnect(spaceId, id, p.name)
}
