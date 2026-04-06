package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false, unique = true)
    var token: String = "",

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Column(nullable = false)
    var expiryDate: Instant = Instant.now(),

    @Column(nullable = false)
    var isRevoked: Boolean = false,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    @PrePersist
    fun prePersist() {
        createdAt = Instant.now()
    }
}