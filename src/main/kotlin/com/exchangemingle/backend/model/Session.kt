package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "sessions",
    indexes = [
        Index(name = "idx_session_teacher", columnList = "teacher_id"),
        Index(name = "idx_session_learner", columnList = "learner_id"),
        Index(name = "idx_session_status", columnList = "status"),
        Index(name = "idx_session_scheduled", columnList = "scheduled_at")
    ]
)
class Session(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    var teacher: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id", nullable = false)
    var learner: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    var skill: Skill? = null,

    @Column(nullable = false)
    var durationMinutes: Int = 30,

    @Column(nullable = false)
    var creditsUsed: Double = 0.0,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: SessionStatus = SessionStatus.PENDING,

    @Column
    var scheduledAt: LocalDateTime? = null,

    @Column
    var completedAt: LocalDateTime? = null,

    // Legacy single-rating fields (kept for DB compatibility, prefer the ones below)
    @Column(length = 1000)
    var feedback: String? = null,

    @Column
    var rating: Int? = null,

    // Separate ratings for each party
    @Column
    var teacherRating: Int? = null,

    @Column(length = 1000)
    var teacherFeedback: String? = null,

    @Column
    var studentRating: Int? = null,

    @Column(length = 1000)
    var studentFeedback: String? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var creditsHeld: Double = 0.0,

    @Column
    var studentJoinedAt: LocalDateTime? = null,

    @Column
    var teacherJoinedAt: LocalDateTime? = null,

    @Column
    var actualStartTime: LocalDateTime? = null,

    @Column
    var actualEndTime: LocalDateTime? = null,

    @Column(length = 1000)
    var declineReason: String? = null,

    @Column(length = 500)
    var videoCallLink: String? = null,
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

enum class SessionStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    DECLINED,
    AUTO_DECLINED,
    STUDENT_NO_SHOW,
    TEACHER_NO_SHOW,
    BOTH_NO_SHOW,
    LATE_CANCELLATION,
    IN_PROGRESS
}