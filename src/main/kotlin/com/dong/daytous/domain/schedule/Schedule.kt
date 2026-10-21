package com.dong.daytous.domain.schedule

import com.dong.daytous.domain.sharedspace.SharedSpace
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDateTime
import java.util.Objects
import java.util.UUID

@Entity
@Table(name = "schedule")
class Schedule(
    @Column(nullable = false)
    var title: String,

    @Column(length = 1000)
    var description: String? = null,

    @Column(nullable = false)
    var startDateTime: LocalDateTime,

    @Column(nullable = false)
    var endDateTime: LocalDateTime,

    @Column(nullable = false)
    var isAllDay: Boolean = false,

    @Column(nullable = false)
    val createdBy: Long, // User ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_space_id", nullable = false)
    val sharedSpace: SharedSpace,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null

    @Version
    var version: Long? = null

    // Retain the remote identity after deletion so a later pull cannot recreate it.
    var deletedAt: LocalDateTime? = null
    var googleUserId: Long? = null
    var googleCalendarId: String? = null

    @Column(name = "google_event_id")
    var googleEventId: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY

    @Column(name = "last_synced_at")
    var lastSyncedAt: LocalDateTime? = null

    @Column(name = "last_modified_at")
    var lastModifiedAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "google_last_modified_at")
    var googleLastModifiedAt: LocalDateTime? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Schedule) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = Objects.hash(id)

    override fun toString(): String = "Schedule(id=$id, title='$title', start=$startDateTime, end=$endDateTime)"
}
