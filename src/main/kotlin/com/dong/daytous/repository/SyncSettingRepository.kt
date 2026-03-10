package com.dong.daytous.repository

import com.dong.daytous.domain.user.SyncSetting
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface SyncSettingRepository : JpaRepository<SyncSetting, Long> {
    fun findByUserId(userId: Long): Optional<SyncSetting>
}
