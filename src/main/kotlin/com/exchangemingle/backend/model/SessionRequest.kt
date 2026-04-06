package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "session_requests",
    indexes = [
        Index(name = "idx_sr_learner", columnList = "learner_id"),
        Index(name = "idx_sr_skill", columnList = "skill_id"),
        Index(name = "idx_sr_status", columnList = "status")
    ]
)
class SessionRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id", nullable = false)
    var learner: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    var skill: Skill? = null,

    @Column(nullable = false)
    var durationMinutes: Int = 30,

    @Column(length = 1000)
    var message: String? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: SessionRequestStatus = SessionRequestStatus.OPEN,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    var acceptedBy: User? = null,

    @Column
    var acceptedAt: LocalDateTime? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    /** Number of times teachers have viewed this request */
    @Column(nullable = false)
    var viewCount: Int = 0,

    /** Number of teachers who expressed interest in this request */
    @Column(nullable = false)
    var interestCount: Int = 0
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

enum class SessionRequestStatus {
    OPEN,
    ACCEPTED,
    EXPIRED,
    CANCELLED
}