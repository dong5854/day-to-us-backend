package com.dong.daytous.domain.push

import com.dong.daytous.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDateTime
import java.util.Objects
import java.util.UUID

@Entity
@Table(
    name = "push_subscription",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "endpoint"])],
)
class PushSubscription(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 2048)
    val endpoint: String,

    @Column(nullable = false, length = 512)
    val p256dh: String,

    @Column(nullable = false, length = 512)
    val auth: String,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PushSubscription) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = Objects.hash(id)
}
