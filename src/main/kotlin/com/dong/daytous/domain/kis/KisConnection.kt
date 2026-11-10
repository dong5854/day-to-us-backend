package com.dong.daytous.domain.kis

import com.dong.daytous.domain.savings.SavingsPlan
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "kis_connection")
class KisConnection(
    @Id @Column(name = "plan_id") val planId: UUID,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "plan_id", insertable = false, updatable = false)
    val plan: SavingsPlan,
    val ownerEmail: String,
    val productCode: String,
    @Column(length = 2048) val accountEncrypted: String,
    @Column(length = 2048) val appKeyEncrypted: String,
    @Column(length = 2048) val appSecretEncrypted: String,
    val accountLastFour: String,
) {
    @Column(length = 4096) var tokenEncrypted: String? = null
    var tokenExpiresAt: Instant? = null
    var lastAttemptAt: Instant? = null
    var lastSyncedAt: Instant? = null
    var valuation: Long? = null
    var cash: Long? = null
    var error: String? = null
}
