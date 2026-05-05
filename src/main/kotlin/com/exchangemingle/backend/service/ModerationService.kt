package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.model.*
import com.exchangemingle.backend.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ModerationService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val reportRepository: ReportRepository,
    private val pushNotificationService: PushNotificationService
) {
    private val log = LoggerFactory.getLogger(ModerationService::class.java)

    /**
     * Called in real-time during a video call when offensive gesture or
     * speech is detected on-device. Immediately bans the offending user,8
     * kicks them from the session, and notifies the other participant.
     */
    @Transactional
    fun reportLiveIncident(reporterId: Long, request: LiveIncidentRequest): LiveIncidentResponse {
        val session = sessionRepository.findById(request.sessionId)
            .orElseThrow { RuntimeException("Session not found: ${request.sessionId}") }

        val reporter = userRepository.findById(reporterId)
            .orElseThrow { RuntimeException("Reporter not found: $reporterId") }

        // Determine the offender — must be the other participant
        val offender = when (reporterId) {
            session.teacher?.id -> session.learner
            session.learner?.id -> session.teacher
            else -> throw RuntimeException("Reporter is not a session participant")
        } ?: throw RuntimeException("Other participant not found")

        log.warn(
            "LIVE INCIDENT: session={} reporter={} offender={} type={}",
            request.sessionId, reporterId, offender.id, request.incidentType
        )

        // Create a permanent report record
        val report = Report(
            reporter     = reporter,
            reportedUser = offender,
            session      = session,
            reportType   = ReportType.INAPPROPRIATE_CONTENT,
            description  = buildIncidentDescription(request),
            status       = ReportStatus.RESOLVED   // auto-resolved since action is immediate
        )
        report.resolvedAt = LocalDateTime.now()
        reportRepository.save(report)

        // Immediately ban the offender
        offender.status       = UserStatus.BANNED
        offender.isActive     = false
        offender.suspendedUntil = null          // permanent ban overrides any suspension
        userRepository.save(offender)

        // Mark session as terminated
        session.status        = SessionStatus.CANCELLED
        session.actualEndTime = LocalDateTime.now()
        sessionRepository.save(session)

        // Push: notify banned user
        offender.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                deviceToken = token,
                title       = "Account Banned",
                body        = "Your account has been permanently banned due to a serious violation during a live session.",
                data        = mapOf("type" to "ACCOUNT_BANNED", "sessionId" to request.sessionId.toString())
            )
        }

        // Push: notify the other participant that the session was ended
        reporter.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                deviceToken = token,
                title       = "Session Ended",
                body        = "The session was terminated and the other user has been banned.",
                data        = mapOf("type" to "SESSION_TERMINATED_VIOLATION", "sessionId" to request.sessionId.toString())
            )
        }

        log.info("User {} banned for live incident in session {}", offender.id, request.sessionId)

        return LiveIncidentResponse(
            success     = true,
            action      = "BANNED",
            message     = "User has been permanently banned. Session ended.",
            offenderBanned = true
        )
    }

    /**
     * Called when the camera-off grace period expires.
     * After [CAMERA_OFF_GRACE_SECONDS] seconds of camera-off, the offending
     * party is warned. If still off after [CAMERA_OFF_BAN_SECONDS], they are
     * removed from the session (not permanently banned — first offence is a session kick).
     */
    @Transactional
    fun reportCameraViolation(reporterId: Long, request: CameraViolationRequest): LiveIncidentResponse {
        val session = sessionRepository.findById(request.sessionId)
            .orElseThrow { RuntimeException("Session not found: ${request.sessionId}") }

        val offender = when (reporterId) {
            session.teacher?.id -> session.learner
            session.learner?.id -> session.teacher
            else -> throw RuntimeException("Reporter is not a session participant")
        } ?: throw RuntimeException("Other participant not found")

        // Increment reliability score penalty
        offender.reliabilityScore = (offender.reliabilityScore - 5).coerceAtLeast(0)
        userRepository.save(offender)

        // Terminate the session — the offender refused to turn camera on
        session.status        = SessionStatus.CANCELLED
        session.actualEndTime = LocalDateTime.now()
        sessionRepository.save(session)

        offender.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                deviceToken = token,
                title       = "Session Terminated",
                body        = "Your session was ended because you did not turn on your camera within the required time.",
                data        = mapOf("type" to "SESSION_TERMINATED_NO_CAMERA", "sessionId" to request.sessionId.toString())
            )
        }

        return LiveIncidentResponse(
            success        = true,
            action         = "SESSION_TERMINATED",
            message        = "Session ended — other user did not enable their camera.",
            offenderBanned = false
        )
    }

    /**
     * Post-call: both parties submit rating + optional report against the other.
     * Each party can only submit once. Ratings are stored on the Session entity.
     */
    @Transactional
    fun submitPostCallReview(submitterId: Long, request: PostCallReviewRequest): PostCallReviewResponse {
        val session = sessionRepository.findById(request.sessionId)
            .orElseThrow { RuntimeException("Session not found: ${request.sessionId}") }

        val isTeacher = session.teacher?.id == submitterId
        val isLearner = session.learner?.id == submitterId
        if (!isTeacher && !isLearner) throw RuntimeException("You are not part of this session")

        // Idempotency: don't let them submit twice
        if (isTeacher && session.teacherRating != null)
            return PostCallReviewResponse(success = false, message = "You have already submitted your review.")
        if (isLearner && session.studentRating != null)
            return PostCallReviewResponse(success = false, message = "You have already submitted your review.")

        // Save rating on session
        if (isTeacher) {
            session.teacherRating   = request.rating
            session.teacherFeedback = request.feedback
        } else {
            session.studentRating   = request.rating
            session.studentFeedback = request.feedback
        }

        // If both have rated, mark completed
        val bothRated = (if (isTeacher) request.rating else session.teacherRating) != null &&
                (if (isLearner) request.rating else session.studentRating) != null
        if (bothRated) session.status = SessionStatus.COMPLETED

        sessionRepository.save(session)

        // If they also want to report, create a report
        if (request.reportReason != null && request.reportReason.isNotBlank()) {
            val reporter     = userRepository.findById(submitterId).orElseThrow()
            val reportedUser = if (isTeacher) session.learner else session.teacher

            val report = Report(
                reporter     = reporter,
                reportedUser = reportedUser,
                session      = session,
                reportType   = request.reportType ?: ReportType.INAPPROPRIATE_BEHAVIOR,
                description  = request.reportReason,
                status       = ReportStatus.PENDING
            )
            reportRepository.save(report)

            // Auto-ban check: ≥3 confirmed reports → immediate ban
            reportedUser?.let { user ->
                val count = reportRepository.countResolvedReportsByUser(user)
                if (count >= 3) {
                    user.status   = UserStatus.BANNED
                    user.isActive = false
                    userRepository.save(user)

                    user.fcmToken?.let { token ->
                        pushNotificationService.sendNotification(
                            deviceToken = token,
                            title       = "Account Banned",
                            body        = "Your account has been banned due to repeated violations.",
                            data        = mapOf("type" to "ACCOUNT_BANNED")
                        )
                    }
                    log.info("User {} auto-banned after {} reports", user.id, count)
                }
            }
        }

        return PostCallReviewResponse(
            success = true,
            message = "Review submitted successfully."
        )
    }

    private fun buildIncidentDescription(req: LiveIncidentRequest): String {
        val typeLabel = when (req.incidentType) {
            IncidentType.OFFENSIVE_GESTURE -> "offensive gesture (${req.gestureLabel ?: "unknown"})"
            IncidentType.OFFENSIVE_SPEECH  -> "offensive speech"
        }
        return "Automatic detection of $typeLabel during live session ${req.sessionId}. " +
                "Confidence: ${req.confidence?.let { "%.0f%%".format(it * 100) } ?: "N/A"}. " +
                (req.transcriptSnippet?.let { "Snippet: \"$it\"" } ?: "")
    }
}