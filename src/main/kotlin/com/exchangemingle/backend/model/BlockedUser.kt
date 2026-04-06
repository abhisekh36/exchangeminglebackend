package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "blocked_users",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["blocker_id", "blocked_id"])
    ],
    indexes = [
        Index(name = "idx_blocker", columnList = "blocker_id"),
        Index(name = "idx_blocked", columnList = "blocked_id")
    ]
)
class BlockedUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "blocker_id", nullable = false)
    var blocker: User? = null,

    @ManyToOne
    @JoinColumn(name = "blocked_id", nullable = false)
    var blocked: User? = null,

    @Column(length = 500)
    var reason: String? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)