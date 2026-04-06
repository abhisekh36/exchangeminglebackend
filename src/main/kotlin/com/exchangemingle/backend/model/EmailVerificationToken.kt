package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false, unique = true, length = 6)
    var code: String = "",

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