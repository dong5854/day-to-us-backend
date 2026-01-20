package com.dong.daytous.service

import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.dto.SharedSpaceResponse
import com.dong.daytous.dto.UserResponse
import com.dong.daytous.dto.toResponse
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SharedSpaceService(
    private val sharedSpaceRepository: SharedSpaceRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createSharedSpace(
        name: String,
        email: String,
    ): SharedSpaceResponse {
        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { EntityNotFoundException("User not found") }

        if (user.sharedSpace != null) {
            throw IllegalStateException("User already belongs to a shared space")
        }

        val newSpace = SharedSpace(name = name)
        val savedSpace = sharedSpaceRepository.save(newSpace)

        user.sharedSpace = savedSpace

        return savedSpace.toResponse()
    }

    @Transactional
    fun joinSharedSpace(
        inviteCode: String,
        email: String,
    ): SharedSpaceResponse {
        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { EntityNotFoundException("User not found") }

        if (user.sharedSpace != null) {
            throw IllegalStateException("User already belongs to a shared space")
        }

        val space =
            sharedSpaceRepository
                .findByInviteCode(inviteCode)
                .orElseThrow { EntityNotFoundException("Invalid invite code") }

        if (space.users.size >= 2) {
            throw IllegalStateException("Shared space is full")
        }

        user.sharedSpace = space

        return space.toResponse()
    }

    @Transactional(readOnly = true)
    fun getMySharedSpaces(email: String): List<SharedSpaceResponse> {
        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { EntityNotFoundException("User not found") }

        return listOfNotNull(user.sharedSpace?.toResponse())
    }

    @Transactional(readOnly = true)
    fun getMembers(email: String): List<UserResponse> {
        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { EntityNotFoundException("User not found") }

        return user.sharedSpace?.users?.map { it.toResponse() } ?: emptyList()
    }
}
