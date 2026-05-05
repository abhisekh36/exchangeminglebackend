package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "reports")
class Report(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    var reporter: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id")
    var reportedUser: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    var session: Session? = null,

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    var reportType: ReportType = ReportType.INAPPROPRIATE_BEHAVIOR,

    @Column(nullable = false, length = 1000)
    var description: String = "",

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: ReportStatus = ReportStatus.PENDING,

    @Column(length = 1000)
    var adminNotes: String? = null,

    @Column
    var resolvedAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    var resolvedBy: User? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
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

enum class ReportType {
    INAPPROPRIATE_BEHAVIOR,
    HARASSMENT,
    SPAM,
    FAKE_PROFILE,
    INAPPROPRIATE_CONTENT,
    SCAM,
    OTHER
}

enum class ReportStatus {
    PENDING,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED
}