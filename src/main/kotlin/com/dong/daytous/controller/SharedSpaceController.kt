package com.dong.daytous.controller

import com.dong.daytous.dto.SharedSpaceRequest
import com.dong.daytous.dto.SharedSpaceResponse
import com.dong.daytous.service.SharedSpaceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/shared-spaces")
class SharedSpaceController(
    private val sharedSpaceService: SharedSpaceService
) {
    @PostMapping
    fun createSharedSpace(@Valid @RequestBody request: SharedSpaceRequest): ResponseEntity<SharedSpaceResponse> {
        val createdSpace = sharedSpaceService.createSharedSpace(request.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSpace)
    }

    @GetMapping
    fun getAllSharedSpaces(): List<SharedSpaceResponse> {
        return sharedSpaceService.getAllSharedSpaces()
    }
}
