package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "notifications",
    indexes = [
        Index(name = "idx_notif_user", columnList = "user_id"),
        Index(name = "idx_notif_read", columnList = "is_read")
    ]
)
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    var type: NotificationType = NotificationType.SESSION_REMINDER,

    @Column(nullable = false, length = 200)
    var title: String = "",

    @Column(nullable = false, length = 500)
    var body: String = "",

    @Column(nullable = false)
    var isRead: Boolean = false,

    @Column(name = "related_entity_type", length = 30)
    var relatedEntityType: String? = null,

    @Column(name = "related_entity_id")
    var relatedEntityId: Long? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun prePersist() {
        createdAt = LocalDateTime.now()
    }
}

enum class NotificationType {
    SESSION_REMINDER,
    SESSION_REQUEST_RECEIVED,
    SESSION_ACCEPTED,
    SESSION_DECLINED,
    RATING_REMINDER,
    CREDIT_EARNED,
    CREDIT_LOW,
    STREAK_MILESTONE,
    BADGE_UNLOCKED,
    REQUEST_EXPIRED,
    NEW_MATCH
}

@Entity
@Table(name = "achievements")
class Achievement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false, unique = true, length = 50)
    var key: String = "",

    @Column(nullable = false, length = 100)
    var name: String = "",

    @Column(length = 300)
    var description: String? = null,

    @Column(nullable = false)
    var creditReward: Double = 0.0,

    @Column(nullable = false)
    var requiredCount: Int = 1,

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    var category: AchievementCategory = AchievementCategory.TEACHING
)

enum class AchievementCategory {
    TEACHING,
    LEARNING,
    STREAK,
    SOCIAL,
    MILESTONE
}

@Entity
@Table(
    name = "user_achievements",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "achievement_id"])],
    indexes = [Index(name = "idx_ua_user", columnList = "user_id")]
)
class UserAchievement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "achievement_id", nullable = false)
    var achievement: Achievement? = null,

    @Column(nullable = false)
    var currentCount: Int = 0,

    @Column(nullable = false)
    var isUnlocked: Boolean = false,

    @Column
    var unlockedAt: LocalDateTime? = null
)