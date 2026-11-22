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

    @org.springframework.data.jpa.repository.Modifying
    @Query("update PersonalToken t set t.revokedAt = :now where t.owner.id = :ownerId and t.revokedAt is null")
    fun revokePreviousMembershipTokens(ownerId: Long, now: java.time.Instant): Int

    fun countBySharedSpaceId(spaceId: UUID): Long
}
