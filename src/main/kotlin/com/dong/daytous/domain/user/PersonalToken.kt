package com.dong.daytous.domain.user

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class TokenAccess { READ, EDIT }

@Entity
@Table(name = "personal_token")
class PersonalToken(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 64) val digest: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) val owner: User,
    @Column(nullable = false) val spaceId: UUID,
    @Column(nullable = false, length = 80) val name: String,
    @Column(nullable = false, length = 8) @Enumerated(EnumType.STRING) val access: TokenAccess,
    val createdAt: Instant,
    val expiresAt: Instant,
    var lastUsedAt: Instant? = null,
    var revokedAt: Instant? = null,
    var windowStart: Instant = createdAt,
    var requestCount: Int = 0,
)
