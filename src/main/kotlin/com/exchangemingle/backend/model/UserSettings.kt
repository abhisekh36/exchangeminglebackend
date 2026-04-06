package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_settings")
class UserSettings(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User? = null,

    @Column(nullable = false)
    var pushNotificationsEnabled: Boolean = true,

    @Column(nullable = false)
    var emailNotificationsEnabled: Boolean = true,

    @Column(nullable = false)
    var sessionRemindersEnabled: Boolean = true,

    @Column(nullable = false)
    var marketingEmailsEnabled: Boolean = false,

    @Column(nullable = false)
    var profileVisibility: ProfileVisibility = ProfileVisibility.PUBLIC,

    @Column(nullable = false)
    var showOnlineStatus: Boolean = true,

    @Column(nullable = false)
    var allowSessionRequests: Boolean = true,

    @Column(length = 10)
    var preferredLanguage: String = "en",

    @Column(length = 50)
    var timezone: String = "UTC",

    @Column(nullable = false)
    var autoAcceptSessions: Boolean = false,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}

enum class ProfileVisibility {
    PUBLIC,      // Everyone can see profile
    CONNECTIONS, // Only people who've had sessions with you
    PRIVATE      // Hidden from discovery
}