package com.dong.daytous.domain.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

enum class SyncDirection {
    BIDIRECTIONAL,
    APP_TO_GOOGLE,
    GOOGLE_TO_APP,
}

@Entity
@Table(name = "sync_setting")
class SyncSetting(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Column(nullable = false)
    var syncEnabled: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var syncDirection: SyncDirection = SyncDirection.BIDIRECTIONAL,

    @Column(nullable = false)
    var googleCalendarId: String = "primary",
)
