package com.exchangemingle.backend.dto

import com.exchangemingle.backend.model.SessionStatus
import jakarta.validation.constraints.*
import java.time.LocalDateTime

data class CreateSessionRequest(
    @field:NotNull(message = "Teacher ID is required")
    val teacherId: Long,

    @field:NotNull(message = "Skill ID is required")
    val skillId: Long,

    @field:NotNull(message = "Duration is required")
    @field:Min(value = 30, message = "Minimum session duration is 30 minutes")
    @field:Max(value = 180, message = "Duration cannot exceed 180 minutes")
    val durationMinutes: Int,

    @field:NotNull(message = "Scheduled time is required")
    @field:Future(message = "Scheduled time must be in the future")
    val scheduledAt: LocalDateTime,

    /** Optional: if the learner is booking within a specific availability slot, provide the slot ID */
    val availabilitySlotId: Long? = null
)

data class UpdateSessionStatusRequest(
    @field:NotNull(message = "Status is required")
    val status: SessionStatus
)

data class AddSessionFeedbackRequest(
    @field:NotBlank(message = "Feedback is required")
    @field:Size(min = 10, max = 1000, message = "Feedback must be between 10 and 1000 characters")
    val feedback: String,

    @field:NotNull(message = "Rating is required")
    @field:Min(value = 1, message = "Rating must be at least 1")
    @field:Max(value = 5, message = "Rating cannot exceed 5")
    val rating: Int
)

data class SessionResponse(
    // IDs
    val id: Long,
    val teacherId: Long,
    val studentId: Long,
    val skillId: Long,
    // Names (flat - what the frontend needs)
    val teacherName: String,
    val studentName: String,
    val teacherAvatarUrl: String? = null,
    val studentAvatarUrl: String? = null,
    val skillName: String,
    // Timing - ISO-8601 strings for frontend
    val scheduledStartTime: String,
    val scheduledEndTime: String,
    val actualStartTime: String? = null,
    val actualEndTime: String? = null,
    // Status & credits
    val status: SessionStatus,
    val creditsAmount: Int,
    // Video call
    val channelName: String? = null,
    val videoCallLink: String? = null,
    // Ratings (separate for teacher and student)
    val teacherRating: Int? = null,
    val studentRating: Int? = null,
    val teacherFeedback: String? = null,
    val studentFeedback: String? = null,
    // Timestamps
    val createdAt: String,
    val updatedAt: String
)

data class PagedSessionResponse(
    val content: List<SessionResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)

data class SessionStatistics(
    val totalSessions: Long,
    val completedSessions: Long,
    val pendingSessions: Long,
    val cancelledSessions: Long,
    val averageRating: Double?,
    val totalCreditsEarned: Double,
    val totalCreditsSpent: Double
)