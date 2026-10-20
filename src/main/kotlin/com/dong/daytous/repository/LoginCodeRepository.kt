package com.dong.daytous.repository

import com.dong.daytous.domain.user.LoginCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface LoginCodeRepository : JpaRepository<LoginCode, String> {
    @Modifying
    @Query("delete from LoginCode c where c.codeHash = :hash and c.bindingHash = :binding and c.expiresAt > :now")
    fun consume(@Param("hash") hash: String, @Param("binding") binding: String, @Param("now") now: Instant): Int

    @Modifying
    @Query("delete from LoginCode c where c.expiresAt <= :now")
    fun deleteExpired(@Param("now") now: Instant)
}
