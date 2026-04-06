package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.model.SessionStatus
import com.exchangemingle.backend.model.SkillRole
import com.exchangemingle.backend.repository.UserRepository
import com.exchangemingle.backend.repository.UserSkillRepository
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.SessionService
import com.exchangemingle.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.ZoneId

@RestController
@RequestMapping("/api/sessions")
class SessionController(
    private val sessionService: SessionService,
    private val userService: UserService,
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val userSkillRepository: UserSkillRepository
) {

    private fun tokenEmail(authHeader: String): String {
        return jwtService.extractUsername(authHeader.substring(7))
    }

    @PostMapping
    fun createSession(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: CreateSessionRequest
    ): ResponseEntity<SessionResponse> {
        val learner = userService.findByEmail(tokenEmail(authHeader))
        val session = sessionService.createSession(learner.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(session)
    }

    @GetMapping("/{id}")
    fun getSessionById(@PathVariable id: Long): ResponseEntity<SessionResponse> {
        return ResponseEntity.ok(sessionService.getSessionById(id))
    }

    @GetMapping
    fun getAllSessions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedSessionResponse> {
        return ResponseEntity.ok(sessionService.getAllSessions(page, size))
    }

    @GetMapping("/my-sessions")
    fun getMySessions(
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<PagedSessionResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        val sessionStatus = status?.uppercase()?.let {
            runCatching { SessionStatus.valueOf(it) }.getOrNull()
        }
        return ResponseEntity.ok(sessionService.getSessionsByUser(user.id, page, size, sessionStatus))
    }

    @GetMapping("/as-teacher")
    fun getSessionsAsTeacher(
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedSessionResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        return ResponseEntity.ok(sessionService.getSessionsByTeacher(user.id, page, size))
    }

    @GetMapping("/as-learner")
    fun getSessionsAsLearner(
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedSessionResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        return ResponseEntity.ok(sessionService.getSessionsByLearner(user.id, page, size))
    }

    @GetMapping("/status/{status}")
    fun getSessionsByStatus(
        @PathVariable status: SessionStatus,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedSessionResponse> {
        return ResponseEntity.ok(sessionService.getSessionsByStatus(status, page, size))
    }

    @PatchMapping("/{id}/status")
    fun updateSessionStatus(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: UpdateSessionStatusRequest
    ): ResponseEntity<SessionResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        return ResponseEntity.ok(sessionService.updateSessionStatus(id, user.id, request))
    }

    @PostMapping("/{id}/feedback")
    fun addFeedback(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: AddSessionFeedbackRequest
    ): ResponseEntity<SessionResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        return ResponseEntity.ok(sessionService.addFeedback(id, user.id, request))
    }

    @GetMapping("/statistics")
    fun getMyStatistics(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<SessionStatistics> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        return ResponseEntity.ok(sessionService.getUserStatistics(user.id))
    }

    @GetMapping("/user/{userId}/statistics")
    fun getUserStatistics(@PathVariable userId: Long): ResponseEntity<SessionStatistics> {
        return ResponseEntity.ok(sessionService.getUserStatistics(userId))
    }

    @PostMapping("/{id}/accept")
    fun acceptSession(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<SessionResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        return ResponseEntity.ok(sessionService.acceptSessionByTeacher(id, user.id))
    }

    /**
     * POST /api/sessions/request
     * Frontend sends: teacherId, skillId (optional), durationMinutes, scheduledStartTime (ISO)
     * If skillId is missing/0, auto-resolves from teacher's active teaching skills.
     */
    @PostMapping("/request")
    fun createSessionFromRequest(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody request: Map<String, Any?>
    ): ResponseEntity<SessionResponse> {
        val learner = userService.findByEmail(tokenEmail(authHeader))

        val teacherId = (request["teacherId"] as? Number)?.toLong()
            ?: throw IllegalArgumentException("teacherId required")

        val rawSkillId = (request["skillId"] as? Number)?.toLong() ?: 0L
        val skillId: Long = if (rawSkillId > 0L) {
            rawSkillId
        } else {
            // Auto-resolve: find teacher's first active teaching skill
            val teacher = userRepository.findById(teacherId)
                .orElseThrow { IllegalArgumentException("Teacher not found: $teacherId") }
            val teacherSkills = userSkillRepository.findByUserAndRoleAndIsActive(
                teacher, SkillRole.TEACHER, true
            )
            teacherSkills.firstOrNull()?.skill?.id
                ?: throw IllegalArgumentException("Teacher has no active teaching skills")
        }

        val duration = (request["durationMinutes"] as? Number)?.toInt() ?: 60
        val scheduledAtStr = request["scheduledStartTime"] as? String
        val scheduledAt = if (scheduledAtStr != null) {
            Instant.parse(scheduledAtStr).atZone(ZoneId.systemDefault()).toLocalDateTime()
        } else {
            java.time.LocalDateTime.now().plusDays(1)
        }

        val createRequest = CreateSessionRequest(
            teacherId = teacherId,
            skillId = skillId,
            durationMinutes = duration,
            scheduledAt = scheduledAt
        )
        val session = sessionService.createSession(learner.id, createRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(session)
    }

    // ===== PUT aliases for frontend compatibility =====

    @PutMapping("/{id}/accept")
    fun acceptSessionPut(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<SessionResponse> = acceptSession(id, authHeader)

    @PutMapping("/{id}/decline")
    fun declineSessionPut(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<SessionResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        return ResponseEntity.ok(sessionService.declineSession(id, user.id, null))
    }

    @PutMapping("/{id}/cancel")
    fun cancelSession(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<MessageResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        sessionService.updateSessionStatus(id, user.id, UpdateSessionStatusRequest(SessionStatus.CANCELLED))
        return ResponseEntity.ok(MessageResponse("Session cancelled"))
    }

    @PutMapping("/{id}/complete")
    fun completeSession(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<SessionResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        return ResponseEntity.ok(
            sessionService.updateSessionStatus(id, user.id, UpdateSessionStatusRequest(SessionStatus.COMPLETED))
        )
    }

    @PostMapping("/{id}/rate")
    fun rateSession(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody request: Map<String, Any?>
    ): ResponseEntity<MessageResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        val rating = (request["rating"] as? Number)?.toInt() ?: 5
        val feedback = request["feedback"] as? String ?: ""
        sessionService.addFeedback(id, user.id, AddSessionFeedbackRequest(feedback = feedback, rating = rating))
        return ResponseEntity.ok(MessageResponse("Rated successfully"))
    }

    @PostMapping("/{id}/decline")
    fun declineSession(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody(required = false) reason: Map<String, String>?
    ): ResponseEntity<SessionResponse> {
        val user = userService.findByEmail(tokenEmail(authHeader))
        return ResponseEntity.ok(sessionService.declineSession(id, user.id, reason?.get("reason")))
    }
}