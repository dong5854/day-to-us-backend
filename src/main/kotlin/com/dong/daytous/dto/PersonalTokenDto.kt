package com.dong.daytous.dto

import com.dong.daytous.domain.user.PersonalToken
import com.dong.daytous.domain.user.TokenAccess
import java.time.Instant
import java.util.UUID

data class TokenRequest(val name: String, val access: TokenAccess = TokenAccess.READ, val days: Int = 30)
data class TokenInfo(val id: UUID, val name: String, val access: TokenAccess, val expiresAt: Instant, val lastUsedAt: Instant?)
fun PersonalToken.info() = TokenInfo(id, name, access, expiresAt, lastUsedAt)
class IssuedToken(val info: TokenInfo, val secret: String)
