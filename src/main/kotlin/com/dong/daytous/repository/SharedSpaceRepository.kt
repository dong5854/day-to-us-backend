package com.dong.daytous.repository

import com.dong.daytous.domain.sharedspace.SharedSpace
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface SharedSpaceRepository : JpaRepository<SharedSpace, UUID> {
    fun findByInviteCode(inviteCode: String): Optional<SharedSpace>
}
