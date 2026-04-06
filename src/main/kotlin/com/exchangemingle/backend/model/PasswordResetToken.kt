package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false, unique = true)
    var token: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Column(nullable = false)
    var expiryDate: Instant = Instant.now(),

    @Column(nullable = false)
    var isUsed: Boolean = false,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    @PrePersist
    fun prePersist() {
        createdAt = Instant.now()
    }
}