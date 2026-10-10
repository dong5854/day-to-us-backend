package com.dong.daytous.repository

import com.dong.daytous.domain.sharedspace.SharedSpace
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface SharedSpaceRepository : JpaRepository<SharedSpace, UUID> {
    fun findByInviteCode(inviteCode: String): Optional<SharedSpace>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SharedSpace s where s.inviteCode = :inviteCode")
    fun findByInviteCodeForUpdate(inviteCode: String): Optional<SharedSpace>
}
