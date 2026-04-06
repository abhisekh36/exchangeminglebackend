package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Represents a single available time slot posted by a teacher.
 * Learners can only book sessions within these published slots.
 */
@Entity
@Table(
    name = "teacher_availability",
    indexes = [
        Index(name = "idx_avail_teacher", columnList = "teacher_id"),
        Index(name = "idx_avail_start", columnList = "slot_start"),
        Index(name = "idx_avail_booked", columnList = "is_booked")
    ]
)
class TeacherAvailability(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    var teacher: User? = null,

    /** Start of the available slot */
    @Column(name = "slot_start", nullable = false)
    var slotStart: LocalDateTime = LocalDateTime.now(),

    /** End of the available slot */
    @Column(name = "slot_end", nullable = false)
    var slotEnd: LocalDateTime = LocalDateTime.now().plusHours(1),

    /** Whether this slot has been booked by a learner */
    @Column(name = "is_booked", nullable = false)
    var isBooked: Boolean = false,

    /** Optional note about the slot (e.g. "Prefer beginners", "Any skill") */
    @Column(name = "note", length = 500)
    var note: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

) {
    @PrePersist
    fun prePersist() {
        createdAt = LocalDateTime.now()
    }
}