package com.dong.daytous.controller

import com.dong.daytous.dto.JoinSharedSpaceRequest
import com.dong.daytous.dto.SharedSpaceRequest
import com.dong.daytous.dto.SharedSpaceResponse
import com.dong.daytous.dto.UserResponse
import com.dong.daytous.service.SharedSpaceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/shared-spaces")
class SharedSpaceController(
    private val sharedSpaceService: SharedSpaceService,
) {
    @PostMapping
    fun createSharedSpace(
        @Valid @RequestBody request: SharedSpaceRequest,
        principal: Principal,
    ): ResponseEntity<SharedSpaceResponse> {
        val createdSpace = sharedSpaceService.createSharedSpace(request.name, principal.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSpace)
    }

    @PostMapping("/join")
    fun joinSharedSpace(
        @RequestBody request: JoinSharedSpaceRequest,
        principal: Principal,
    ): ResponseEntity<SharedSpaceResponse> {
        val joinedSpace = sharedSpaceService.joinSharedSpace(request.inviteCode, principal.name)
        return ResponseEntity.ok(joinedSpace)
    }

    @GetMapping
    fun getMySharedSpaces(principal: Principal): List<SharedSpaceResponse> = sharedSpaceService.getMySharedSpaces(principal.name)

    @GetMapping("/members")
    fun getMembers(principal: Principal): List<UserResponse> = sharedSpaceService.getMembers(principal.name)
}
