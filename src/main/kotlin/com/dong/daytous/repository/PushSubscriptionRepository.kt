package com.dong.daytous.repository

import com.dong.daytous.domain.push.PushSubscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PushSubscriptionRepository : JpaRepository<PushSubscription, UUID> {
    fun findByUserId(userId: Long): List<PushSubscription>
    fun findByUserIdAndEndpoint(userId: Long, endpoint: String): PushSubscription?
    fun findByUserSharedSpaceId(sharedSpaceId: UUID): List<PushSubscription>
    fun deleteByUserIdAndEndpoint(userId: Long, endpoint: String)
}
