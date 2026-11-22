package com.dong.daytous.repository

import com.dong.daytous.domain.user.PersonalToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import java.time.Instant
import java.util.UUID

interface PersonalTokenRepository : JpaRepository<PersonalToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByDigest(digest: String): PersonalToken?
    fun findByOwnerEmailAndSpaceIdAndRevokedAtIsNullOrderByCreatedAtDesc(email: String, spaceId: UUID): List<PersonalToken>
    fun countByOwnerIdAndCreatedAtAfter(ownerId: Long, since: Instant): Long

    @Modifying
    @Query("update PersonalToken t set t.revokedAt = :now where t.owner.email = :email and t.spaceId = :space and t.id = :id and t.revokedAt is null")
    fun revoke(email: String, space: UUID, id: UUID, now: Instant): Int
}
