package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.ModerationService
import com.exchangemingle.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/moderation")
class ModerationController(
    private val moderationService: ModerationService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    /**
     * POST /api/moderation/live-incident
     * Called immediately when on-device ML detects an offensive gesture or speech.
     * Bans the offender and terminates the session instantly.
     */
    @PostMapping("/live-incident")
    fun reportLiveIncident(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: LiveIncidentRequest
    ): ResponseEntity<LiveIncidentResponse> {
        val email      = jwtService.extractUsername(authHeader.substring(7))
        val reporter   = userService.findByEmail(email)
        return ResponseEntity.ok(moderationService.reportLiveIncident(reporter.id, request))
    }

    /**
     * POST /api/moderation/camera-violation
     * Called when camera-off grace period expires. Terminates session.
     */
    @PostMapping("/camera-violation")
    fun reportCameraViolation(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: CameraViolationRequest
    ): ResponseEntity<LiveIncidentResponse> {
        val email    = jwtService.extractUsername(authHeader.substring(7))
        val reporter = userService.findByEmail(email)
        return ResponseEntity.ok(moderationService.reportCameraViolation(reporter.id, request))
    }

    /**
     * POST /api/moderation/post-call-review
     * Called after a session ends. Submits rating (1–5) and optional report
     * against the other participant. Each party can only submit once.
     */
    @PostMapping("/post-call-review")
    fun submitPostCallReview(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: PostCallReviewRequest
    ): ResponseEntity<PostCallReviewResponse> {
        val email      = jwtService.extractUsername(authHeader.substring(7))
        val submitter  = userService.findByEmail(email)
        return ResponseEntity.ok(moderationService.submitPostCallReview(submitter.id, request))
    }
}