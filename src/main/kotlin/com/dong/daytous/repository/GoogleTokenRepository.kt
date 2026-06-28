package com.dong.daytous.repository

import com.dong.daytous.domain.user.GoogleToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface GoogleTokenRepository : JpaRepository<GoogleToken, Long> {
    fun findByUserId(userId: Long): Optional<GoogleToken>
}
