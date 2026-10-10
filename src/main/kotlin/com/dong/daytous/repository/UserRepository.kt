package com.dong.daytous.repository

import com.dong.daytous.domain.user.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    fun findByEmailForUpdate(email: String): Optional<User>

    fun countBySharedSpaceId(spaceId: UUID): Long
}
