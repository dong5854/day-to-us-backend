package com.dong.daytous.service

import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.dto.SharedSpaceResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.SharedSpaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SharedSpaceService(
    private val sharedSpaceRepository: SharedSpaceRepository
) {
    @Transactional
    fun createSharedSpace(name: String): SharedSpaceResponse {
        val newSpace = SharedSpace(name = name)
        val savedSpace = sharedSpaceRepository.save(newSpace)
        return savedSpace.toResponse()
    }

    @Transactional(readOnly = true)
    fun getAllSharedSpaces(): List<SharedSpaceResponse> {
        return sharedSpaceRepository.findAll().map { it.toResponse() }
    }
}
