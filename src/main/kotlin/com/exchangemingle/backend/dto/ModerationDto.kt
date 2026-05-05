package com.exchangemingle.backend.dto

import com.exchangemingle.backend.model.ReportType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

// ── Live incident (real-time during call) ─────────────────────────────────────

enum class IncidentType {
    OFFENSIVE_GESTURE,
    OFFENSIVE_SPEECH
}

data class LiveIncidentRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: Long,

    @field:NotNull(message = "Incident type is required")
    val incidentType: IncidentType,

    /** e.g. "MIDDLE_FINGER", "VULGAR_SIGN" */
    val gestureLabel: String? = null,

    /** 0.0–1.0 ML confidence score */
    val confidence: Float? = null,

    /** Short text snippet (max 200 chars) for speech incidents */
    @field:Size(max = 200)
    val transcriptSnippet: String? = null
)

data class LiveIncidentResponse(
    val success: Boolean,
    val action: String,          // "BANNED" | "SESSION_TERMINATED" | "WARNED"
    val message: String,
    val offenderBanned: Boolean
)

// ── Camera violation ──────────────────────────────────────────────────────────

data class CameraViolationRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: Long,

    /** Seconds camera was off before this call was made */
    val cameraOffSeconds: Int = 0
)

// ── Post-call review ──────────────────────────────────────────────────────────

data class PostCallReviewRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: Long,

    @field:NotNull(message = "Rating is required")
    @field:Min(1)
    @field:Max(5)
    val rating: Int,

    @field:Size(max = 1000)
    val feedback: String? = null,

    /** If they want to report the other user simultaneously */
    @field:Size(min = 10, max = 1000)
    val reportReason: String? = null,

    val reportType: ReportType? = null
)

data class PostCallReviewResponse(
    val success: Boolean,
    val message: String
)