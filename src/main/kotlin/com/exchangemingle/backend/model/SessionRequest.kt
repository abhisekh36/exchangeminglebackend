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
    var interestCount: Int = 0,

    /**
     * Set once the teacher who accepted this request actually picks a date/time
     * and a real Session gets created for it. Before this is set, "accepted"
     * only means the teacher claimed the request — no session exists yet and
     * nothing is on either person's calendar.
     */
    @Column
    var scheduledSessionId: Long? = null
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
    ACCEPTED,   // legacy value, kept only so old rows still deserialize; no
    // longer written going forward — see MATCHED / SCHEDULED
    MATCHED,    // learner has chosen a teacher from the offers; waiting for
    // that teacher to pick a date/time
    SCHEDULED,  // the chosen teacher scheduled a real Session
    EXPIRED,
    CANCELLED
}

/**
 * One teacher's claim on a learner's open request. Multiple teachers can
 * each create their own offer on the same request at the same time — the
 * request itself stays OPEN and visible to every teacher until the learner
 * picks one (see SessionRequestService.chooseOffer). This is what replaced
 * the old single-teacher-exclusive "acceptRequest" behavior, where the
 * first teacher to accept silently locked everyone else out.
 */
@Entity
@Table(
    name = "session_request_offers",
    uniqueConstraints = [UniqueConstraint(name = "uk_offer_request_teacher", columnNames = ["session_request_id", "teacher_id"])],
    indexes = [
        Index(name = "idx_offer_request", columnList = "session_request_id"),
        Index(name = "idx_offer_teacher", columnList = "teacher_id"),
        Index(name = "idx_offer_status", columnList = "status")
    ]
)
class SessionRequestOffer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_request_id", nullable = false)
    var sessionRequest: SessionRequest? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    var teacher: User? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: OfferStatus = OfferStatus.PENDING,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var chosenAt: LocalDateTime? = null,

    /** Set once this (chosen) offer's teacher actually schedules a Session. */
    @Column
    var scheduledSessionId: Long? = null
) {
    @PrePersist
    fun prePersist() {
        createdAt = LocalDateTime.now()
    }
}

enum class OfferStatus {
    PENDING,
    CHOSEN,
    DECLINED,   // learner picked a different teacher for this request
    WITHDRAWN   // teacher pulled their own offer back
}