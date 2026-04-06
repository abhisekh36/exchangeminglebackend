package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "app_users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false)
    var password: String = "",

    @Column(nullable = false)
    var credits: Double = 5.0,

    @Column(nullable = false)
    var isActive: Boolean = true,

    @Column(nullable = false)
    var isEmailVerified: Boolean = false,

    @Column(length = 500)
    var bio: String? = null,

    @Column(length = 500)
    var avatar: String? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(length = 500)
    var fcmToken: String? = null,

    @Column(nullable = false)
    var heldCredits: Double = 0.0,

    @Column(nullable = false)
    var reliabilityScore: Int = 100,

    @Column(nullable = false)
    var noShowCount: Int = 0,

    @Column(nullable = false)
    var lateCancellationCount: Int = 0,

    @Column
    var suspendedUntil: LocalDateTime? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(length = 20)
    var authProvider: String? = null,

    @Column(length = 500)
    var googleId: String? = null,

    @Column(nullable = false)
    var needsNameSetup: Boolean = false

) {
    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}

enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    BANNED
}