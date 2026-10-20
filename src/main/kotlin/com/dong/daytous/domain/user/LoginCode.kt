package com.dong.daytous.domain.user

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "login_code")
class LoginCode(
    @Id val codeHash: String,
    val bindingHash: String,
    val userId: Long,
    val expiresAt: Instant,
)
