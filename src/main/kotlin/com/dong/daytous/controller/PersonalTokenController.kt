package com.dong.daytous.controller

import com.dong.daytous.dto.TokenRequest
import com.dong.daytous.service.PersonalTokenService
import org.springframework.http.*
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/shared-spaces/{spaceId}/api-tokens")
class PersonalTokenController(private val service: PersonalTokenService) {
    @GetMapping
    fun list(@PathVariable spaceId: UUID, auth: Authentication) = service.list(auth.name, spaceId)

    @PostMapping
    fun issue(@PathVariable spaceId: UUID, @RequestBody body: TokenRequest, auth: Authentication): ResponseEntity<*> {
        val issuedAt = auth.details as? Instant
        if (issuedAt == null || issuedAt.isBefore(Instant.now().minusSeconds(600))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("code" to "RECENT_LOGIN_REQUIRED"))
        }
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.issue(auth.name, spaceId, body))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@PathVariable spaceId: UUID, @PathVariable id: UUID, auth: Authentication) =
        service.revoke(auth.name, spaceId, id)
}
