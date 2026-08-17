package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.*
import com.exchangemingle.backend.model.Session
import com.exchangemingle.backend.model.SessionStatus
import com.exchangemingle.backend.model.UserStatus
import com.exchangemingle.backend.repository.SessionRepository
import com.exchangemingle.backend.model.SkillRole
import com.exchangemingle.backend.model.User
import com.exchangemingle.backend.repository.SkillRepository
import com.exchangemingle.backend.repository.UserSkillRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val skillRepository: SkillRepository,
    private val userSkillRepository: UserSkillRepository,
    private val pushNotificationService: PushNotificationService,
    private val userSkillService: UserSkillService,
    private val teacherAvailabilityService: TeacherAvailabilityService,
    private val achievementService: AchievementService
) {

    companion object {
        private const val DEFAULT_CREDITS_PER_MINUTE = 0.1  // fallback if teacher has no rate set
    }

    @Transactional
    fun createSession(learnerId: Long, request: CreateSessionRequest): SessionResponse {
        val learner = userRepository.findById(learnerId)
            .orElseThrow { UserNotFoundException("Learner not found with id: $learnerId") }

        val teacher = userRepository.findById(request.teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found with id: ${request.teacherId}") }

        val skill = skillRepository.findById(request.skillId)
            .orElseThrow { SkillNotFoundException("Skill not found with id: ${request.skillId}") }

        // Self-booking check
        if (learner.id == teacher.id) {
            throw SelfBookingException()
        }


        // Check if learner is suspended
        if (learner.status == UserStatus.SUSPENDED) {
            learner.suspendedUntil?.let { until ->
                if (LocalDateTime.now().isBefore(until)) {
                    throw InvalidSessionOperationException("Your account is suspended until $until")
                } else {
                    learner.status = UserStatus.ACTIVE
                    learner.suspendedUntil = null
                }
            }
        }

        if (learner.status == UserStatus.BANNED) {
            throw InvalidSessionOperationException("Your account has been banned")
        }

        // Look up the teacher's rate for this skill; fall back to default if not set
        val teacherSkill = userSkillRepository
            .findByUserAndSkillAndRole(teacher, skill, SkillRole.TEACHER)
            .orElse(null)
        val creditsPerMinute = if (teacherSkill?.hourlyCredits != null && teacherSkill.hourlyCredits!! > 0)
            teacherSkill.hourlyCredits!! / 60.0
        else
            DEFAULT_CREDITS_PER_MINUTE

        val creditsNeeded = request.durationMinutes * creditsPerMinute

        if (learner.credits < creditsNeeded) {
            throw InsufficientCreditsException(
                "Insufficient credits. Required: $creditsNeeded, Available: ${learner.credits}"
            )
        }

        // ── Conflict check: teacher can't be double-booked ────────────
        val sessionStart = request.scheduledAt
        val sessionEnd   = sessionStart.plusMinutes(request.durationMinutes.toLong())
        // Fetch sessions whose scheduledAt is in a 3-hour window before sessionEnd
        // (3h covers the max session duration), then filter precisely in memory
        val windowStart  = sessionStart.minusHours(3)
        val candidates   = sessionRepository.findConflictingSessionsForTeacher(
            teacher, sessionStart, sessionEnd, windowStart,
            listOf(SessionStatus.PENDING, SessionStatus.CONFIRMED, SessionStatus.IN_PROGRESS)
        )
        // Precise overlap: candidate overlaps if candidate.start < sessionEnd AND candidate.end > sessionStart
        val hasConflict  = candidates.any { existing ->
            val existingEnd = existing.scheduledAt!!.plusMinutes(existing.durationMinutes.toLong())
            existing.scheduledAt!!.isBefore(sessionEnd) && existingEnd.isAfter(sessionStart)
        }
        if (hasConflict) {
            throw InvalidSessionOperationException(
                "Teacher is already booked during that time slot. Please choose a different time."
            )
        }

        learner.credits -= creditsNeeded
        learner.heldCredits += creditsNeeded

        val session = Session(
            teacher = teacher,
            learner = learner,
            skill = skill,
            durationMinutes = request.durationMinutes,
            creditsUsed = creditsNeeded,
            creditsHeld = creditsNeeded,
            scheduledAt = request.scheduledAt,
            status = SessionStatus.PENDING
        )

        val savedSession = sessionRepository.save(session)
        userRepository.save(learner)

        // If learner booked within a specific availability slot, mark it as booked
        request.availabilitySlotId?.let { slotId ->
            try {
                teacherAvailabilityService.markSlotBooked(slotId)
            } catch (e: Exception) {
                // Non-fatal: slot might not exist or already booked — session still proceeds
            }
        }

        // Notify teacher
        teacher.fcmToken?.let { token ->
            pushNotificationService.sendSessionRequestNotification(
                deviceToken = token,
                sessionId   = savedSession.id,
                learnerName = learner.name,
                skillName   = skill.name
            )
        }

        return mapToSessionResponse(savedSession)
    }

    /**
     * Creates a Session directly in CONFIRMED status for a learner request the
     * teacher has already accepted — used by the "Accept & Schedule Session"
     * flow. Unlike [createSession] (which starts a session as PENDING, awaiting
     * a separate teacher-accept step), the teacher here has already committed
     * by accepting the open request, so there's no second accept to wait on.
     * The learner (not the teacher) gets notified, since the teacher is the
     * one taking this action.
     */
    @Transactional
    fun createConfirmedSessionForAcceptedRequest(
        learnerId: Long,
        teacherId: Long,
        skillId: Long,
        durationMinutes: Int,
        scheduledAt: LocalDateTime
    ): SessionResponse {
        val learner = userRepository.findById(learnerId)
            .orElseThrow { UserNotFoundException("Learner not found with id: $learnerId") }

        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found with id: $teacherId") }

        val skill = skillRepository.findById(skillId)
            .orElseThrow { SkillNotFoundException("Skill not found with id: $skillId") }

        if (learner.id == teacher.id) {
            throw SelfBookingException()
        }

        if (learner.status == UserStatus.SUSPENDED) {
            learner.suspendedUntil?.let { until ->
                if (LocalDateTime.now().isBefore(until)) {
                    throw InvalidSessionOperationException("This learner's account is suspended until $until")
                } else {
                    learner.status = UserStatus.ACTIVE
                    learner.suspendedUntil = null
                }
            }
        }

        if (learner.status == UserStatus.BANNED) {
            throw InvalidSessionOperationException("This learner's account has been banned")
        }

        val teacherSkill = userSkillRepository
            .findByUserAndSkillAndRole(teacher, skill, SkillRole.TEACHER)
            .orElse(null)
        val creditsPerMinute = if (teacherSkill?.hourlyCredits != null && teacherSkill.hourlyCredits!! > 0)
            teacherSkill.hourlyCredits!! / 60.0
        else
            DEFAULT_CREDITS_PER_MINUTE

        val creditsNeeded = durationMinutes * creditsPerMinute

        if (learner.credits < creditsNeeded) {
            throw InsufficientCreditsException(
                "Learner has insufficient credits for this session. Required: $creditsNeeded, Available: ${learner.credits}"
            )
        }

        // ── Conflict check: teacher can't be double-booked ────────────
        val sessionStart = scheduledAt
        val sessionEnd   = sessionStart.plusMinutes(durationMinutes.toLong())
        val windowStart  = sessionStart.minusHours(3)
        val candidates   = sessionRepository.findConflictingSessionsForTeacher(
            teacher, sessionStart, sessionEnd, windowStart,
            listOf(SessionStatus.PENDING, SessionStatus.CONFIRMED, SessionStatus.IN_PROGRESS)
        )
        val hasConflict  = candidates.any { existing ->
            val existingEnd = existing.scheduledAt!!.plusMinutes(existing.durationMinutes.toLong())
            existing.scheduledAt!!.isBefore(sessionEnd) && existingEnd.isAfter(sessionStart)
        }
        if (hasConflict) {
            throw InvalidSessionOperationException(
                "You already have a session booked during that time slot. Please choose a different time."
            )
        }

        learner.credits -= creditsNeeded
        learner.heldCredits += creditsNeeded

        val session = Session(
            teacher = teacher,
            learner = learner,
            skill = skill,
            durationMinutes = durationMinutes,
            creditsUsed = creditsNeeded,
            creditsHeld = creditsNeeded,
            scheduledAt = scheduledAt,
            status = SessionStatus.CONFIRMED  // teacher already committed by accepting the request
        )

        val savedSession = sessionRepository.save(session)
        userRepository.save(learner)

        // Notify the learner — the teacher is the one who just scheduled this.
        learner.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                deviceToken = token,
                title = "Session Scheduled! 🎉",
                body = "${teacher.name} scheduled your ${skill.name} session",
                data = mapOf(
                    "type" to "SESSION_CONFIRMED",
                    "sessionId" to savedSession.id.toString()
                )
            )
        }

        return mapToSessionResponse(savedSession)
    }

    @Transactional
    fun acceptSessionByTeacher(sessionId: Long, teacherId: Long): SessionResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found with id: $sessionId") }

        if (session.teacher?.id != teacherId) {
            throw InvalidSessionOperationException("Only the teacher can accept this session")
        }

        if (session.status != SessionStatus.PENDING) {
            throw InvalidSessionOperationException("Only pending sessions can be accepted")
        }

        session.status = SessionStatus.CONFIRMED
        val updatedSession = sessionRepository.save(session)

        session.learner?.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                deviceToken = token,
                title = "Session Confirmed! 🎉",
                body = "${session.teacher?.name} confirmed your session",
                data = mapOf(
                    "type" to "SESSION_CONFIRMED",
                    "sessionId" to sessionId.toString()
                )
            )
        }

        return mapToSessionResponse(updatedSession)
    }

    @Transactional
    fun declineSession(sessionId: Long, teacherId: Long, reason: String?): SessionResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found with id: $sessionId") }

        if (session.teacher?.id != teacherId) {
            throw InvalidSessionOperationException("Only the teacher can decline this session")
        }

        if (session.status != SessionStatus.PENDING) {
            throw InvalidSessionOperationException("Only pending sessions can be declined")
        }

        session.status = SessionStatus.DECLINED
        session.declineReason = reason

        session.learner?.let { learner ->
            learner.heldCredits -= session.creditsHeld
            learner.credits += session.creditsHeld
            userRepository.save(learner)
        }

        val updatedSession = sessionRepository.save(session)

        session.learner?.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                deviceToken = token,
                title = "Session Declined",
                body = "${session.teacher?.name} declined your session request. Credits refunded.",
                data = mapOf(
                    "type" to "SESSION_DECLINED",
                    "sessionId" to sessionId.toString()
                )
            )
        }

        return mapToSessionResponse(updatedSession)
    }

    @Transactional(readOnly = true)
    fun getSessionById(id: Long): SessionResponse {
        val session = sessionRepository.findByIdEager(id)
            ?: throw SessionNotFoundException("Session not found with id: $id")
        return mapToSessionResponse(session)
    }

    /** See SessionController.acknowledgeBooking's doc comment for why this exists. */
    @Transactional
    fun acknowledgeBooking(sessionId: Long, userId: Long): SessionResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found with id: $sessionId") }

        if (session.learner?.id != userId) {
            throw InvalidSessionOperationException("You are not authorized to acknowledge this session")
        }

        session.learnerAcknowledgedBooking = true
        val saved = sessionRepository.save(session)
        return mapToSessionResponse(saved)
    }

    fun getAllSessions(page: Int = 0, size: Int = 20): PagedSessionResponse {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val sessionsPage = sessionRepository.findAll(pageable)

        return PagedSessionResponse(
            content = sessionsPage.content.map { mapToSessionResponse(it) },
            page = sessionsPage.number,
            size = sessionsPage.size,
            totalElements = sessionsPage.totalElements,
            totalPages = sessionsPage.totalPages,
            isLast = sessionsPage.isLast
        )
    }

    fun getSessionsByUser(userId: Long, page: Int = 0, size: Int = 20, status: SessionStatus? = null): PagedSessionResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val sessionsPage = if (status != null) {
            sessionRepository.findByTeacherOrLearnerAndStatusEager(user, status, pageable)
        } else {
            sessionRepository.findByTeacherOrLearnerEager(user, pageable)
        }

        return PagedSessionResponse(
            content = sessionsPage.content.map { mapToSessionResponse(it) },
            page = sessionsPage.number,
            size = sessionsPage.size,
            totalElements = sessionsPage.totalElements,
            totalPages = sessionsPage.totalPages,
            isLast = sessionsPage.isLast
        )
    }

    fun getSessionsByTeacher(teacherId: Long, page: Int = 0, size: Int = 20): PagedSessionResponse {
        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found with id: $teacherId") }

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val sessionsPage = sessionRepository.findByTeacher(teacher, pageable)

        return PagedSessionResponse(
            content = sessionsPage.content.map { mapToSessionResponse(it) },
            page = sessionsPage.number,
            size = sessionsPage.size,
            totalElements = sessionsPage.totalElements,
            totalPages = sessionsPage.totalPages,
            isLast = sessionsPage.isLast
        )
    }

    fun getSessionsByLearner(learnerId: Long, page: Int = 0, size: Int = 20): PagedSessionResponse {
        val learner = userRepository.findById(learnerId)
            .orElseThrow { UserNotFoundException("Learner not found with id: $learnerId") }

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val sessionsPage = sessionRepository.findByLearner(learner, pageable)

        return PagedSessionResponse(
            content = sessionsPage.content.map { mapToSessionResponse(it) },
            page = sessionsPage.number,
            size = sessionsPage.size,
            totalElements = sessionsPage.totalElements,
            totalPages = sessionsPage.totalPages,
            isLast = sessionsPage.isLast
        )
    }

    fun getSessionsByStatus(status: SessionStatus, page: Int = 0, size: Int = 20): PagedSessionResponse {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val sessionsPage = sessionRepository.findByStatus(status, pageable)

        return PagedSessionResponse(
            content = sessionsPage.content.map { mapToSessionResponse(it) },
            page = sessionsPage.number,
            size = sessionsPage.size,
            totalElements = sessionsPage.totalElements,
            totalPages = sessionsPage.totalPages,
            isLast = sessionsPage.isLast
        )
    }

    @Transactional
    fun updateSessionStatus(sessionId: Long, userId: Long, request: UpdateSessionStatusRequest): SessionResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found with id: $sessionId") }

        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        if (session.teacher?.id != userId && session.learner?.id != userId) {
            throw InvalidSessionOperationException("You are not authorized to update this session")
        }

        when (request.status) {
            SessionStatus.CONFIRMED -> {
                if (session.status != SessionStatus.PENDING) {
                    throw InvalidSessionOperationException("Only pending sessions can be confirmed")
                }
                if (session.teacher?.id != userId) {
                    throw InvalidSessionOperationException("Only the teacher can confirm a session")
                }
            }
            SessionStatus.COMPLETED -> {
                // A session that was actually joined is IN_PROGRESS by the time
                // anyone can meaningfully call "complete" on it (generateToken
                // flips CONFIRMED -> IN_PROGRESS the moment either party joins) —
                // requiring CONFIRMED here meant a genuinely-held session could
                // never be marked complete through this endpoint at all, so it
                // just sat there IN_PROGRESS (and therefore still joinable)
                // indefinitely, which is exactly how a learner could open the
                // call again long after the real session happened and trigger a
                // bogus no-show refund on top of credits the teacher already earned.
                if (session.status != SessionStatus.CONFIRMED && session.status != SessionStatus.IN_PROGRESS) {
                    throw InvalidSessionOperationException("Only confirmed or in-progress sessions can be completed")
                }
                session.completedAt = LocalDateTime.now()
                session.actualEndTime = LocalDateTime.now()

                session.teacher?.let { teacher ->
                    teacher.credits += session.creditsHeld
                    // Reliability is earned through verified activity — actually
                    // holding a scheduled session end-to-end is the clearest
                    // signal of that, so both participants get a small bump
                    // toward the 100 ceiling each time one completes normally.
                    teacher.reliabilityScore = (teacher.reliabilityScore + 2).coerceAtMost(100)
                    session.learner?.let { learner ->
                        learner.heldCredits -= session.creditsHeld
                        learner.reliabilityScore = (learner.reliabilityScore + 2).coerceAtMost(100)
                        userRepository.save(learner)
                    }
                    userRepository.save(teacher)
                }
            }
            SessionStatus.CANCELLED -> {
                if (session.status == SessionStatus.COMPLETED) {
                    throw InvalidSessionOperationException("Completed sessions cannot be cancelled")
                }

                if (session.status != SessionStatus.COMPLETED) {
                    session.learner?.let { learner ->
                        learner.heldCredits -= session.creditsHeld
                        learner.credits += session.creditsHeld
                        userRepository.save(learner)
                    }
                }
            }
            SessionStatus.PENDING -> {
                throw InvalidSessionOperationException("Cannot change status back to pending")
            }
            else -> {
                // Handle other statuses if needed
            }
        }

        session.status = request.status
        val updatedSession = sessionRepository.save(session)

        // Teaching/learning achievements ("First Steps", "Dedicated Teacher",
        // "Curious Mind", etc.) count COMPLETED sessions, but — same root
        // cause as the profile achievement — nothing ever triggered a
        // recheck when a session actually completed, so they never unlocked
        // no matter how many sessions someone finished.
        if (request.status == SessionStatus.COMPLETED) {
            session.teacher?.let { achievementService.checkAndUpdate(it.id) }
            session.learner?.let { achievementService.checkAndUpdate(it.id) }
        }

        return mapToSessionResponse(updatedSession)
    }

    @Transactional
    fun addFeedback(sessionId: Long, userId: Long, request: AddSessionFeedbackRequest): SessionResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found with id: $sessionId") }

        if (session.teacher?.id != userId && session.learner?.id != userId) {
            throw InvalidSessionOperationException("You are not part of this session")
        }

        if (session.status != SessionStatus.COMPLETED) {
            throw InvalidSessionOperationException("Feedback can only be added to completed sessions")
        }

        if (session.teacher?.id == userId) {
            if (session.teacherRating != null) {
                throw InvalidSessionOperationException("You have already rated this session")
            }
            session.teacherRating = request.rating
            session.teacherFeedback = request.feedback
            // Teacher is rating the student — apply the reliability effect to
            // the STUDENT, since that's who's being rated here.
            session.learner?.let { applyRatingToReliability(it, request.rating) }
        } else {
            if (session.studentRating != null) {
                throw InvalidSessionOperationException("You have already rated this session")
            }
            session.studentRating = request.rating
            session.studentFeedback = request.feedback
            // Keep legacy field in sync for queries that use session.rating
            session.rating = request.rating
            session.feedback = request.feedback
            // Student is rating the teacher — apply the reliability effect to
            // the TEACHER.
            session.teacher?.let { applyRatingToReliability(it, request.rating) }
        }

        val updatedSession = sessionRepository.save(session)
        return mapToSessionResponse(updatedSession)
    }

    /**
     * Reliability is meant to be earned through verified activity, not just
     * handed out at a fixed 100 — this is one of the few concrete signals of
     * "verified activity" available: another real person's rating of a
     * session that actually happened. A strong rating nudges the recipient's
     * score up (toward the 100 ceiling); a poor one nudges it down. Small
     * deltas by design — reliability should move gradually with a consistent
     * pattern of behavior, not swing on a single rating.
     */
    private fun applyRatingToReliability(user: User, rating: Int) {
        val delta = when {
            rating >= 4 -> 3
            rating <= 2 -> -5
            else        -> 0   // a "3" is neutral — neither rewarded nor penalized
        }
        if (delta == 0) return
        user.reliabilityScore = (user.reliabilityScore + delta).coerceIn(0, 100)
        userRepository.save(user)
    }

    fun getUserStatistics(userId: Long): SessionStatistics {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        val totalAsTeacher = sessionRepository.countByTeacher(user)
        val totalAsLearner = sessionRepository.countByLearner(user)
        val totalSessions = totalAsTeacher + totalAsLearner

        val completedAsTeacher = sessionRepository.countByTeacherAndStatus(user, SessionStatus.COMPLETED)
        val completedAsLearner = sessionRepository.countByLearnerAndStatus(user, SessionStatus.COMPLETED)
        val completedSessions = completedAsTeacher + completedAsLearner

        val pendingAsTeacher = sessionRepository.countByTeacherAndStatus(user, SessionStatus.PENDING)
        val pendingAsLearner = sessionRepository.countByLearnerAndStatus(user, SessionStatus.PENDING)
        val pendingSessions = pendingAsTeacher + pendingAsLearner

        val cancelledAsTeacher = sessionRepository.countByTeacherAndStatus(user, SessionStatus.CANCELLED)
        val cancelledAsLearner = sessionRepository.countByLearnerAndStatus(user, SessionStatus.CANCELLED)
        val cancelledSessions = cancelledAsTeacher + cancelledAsLearner

        val averageRating = sessionRepository.getAverageRatingForTeacher(user)
        val totalCreditsEarned = sessionRepository.getTotalCreditsEarnedByTeacher(user) ?: 0.0
        val totalCreditsSpent = sessionRepository.getTotalCreditsSpentByLearner(user) ?: 0.0

        return SessionStatistics(
            totalSessions = totalSessions,
            completedSessions = completedSessions,
            pendingSessions = pendingSessions,
            cancelledSessions = cancelledSessions,
            averageRating = averageRating,
            totalCreditsEarned = totalCreditsEarned,
            totalCreditsSpent = totalCreditsSpent
        )
    }

    /**
     * Runs every 30s. Auto-starts sessions whose scheduled time has passed (up to 20 min after).
     * Sends "JOIN NOW" push to both parties at scheduled time.
     */
    @Scheduled(fixedRate = 30_000)
    fun autoStartSessions() {
        try {
            val now = LocalDateTime.now()
            val due = sessionRepository.findSessionsDueToStart(now.minusMinutes(2), now)
            for (session in due) {
                try {
                    // Only send push notification, don't change status
                    // Status transitions to IN_PROGRESS when a user actually joins (generateToken)
                    sendJoinNowPush(session)
                } catch (e: Exception) {
                    println("Failed to notify session ${session.id}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("autoStartSessions error: ${e.message}")
        }
    }

    private fun sendJoinNowPush(session: Session) {
        val joinData = mapOf(
            "type"      to "SESSION_JOIN_NOW",
            "sessionId" to session.id.toString(),
            "action"    to "OPEN_VIDEO_CALL"
        )
        val skill = session.skill?.name ?: "Session"
        session.teacher?.fcmToken?.let { token ->
            pushNotificationService.sendNotification(deviceToken = token,
                title = "📹 Session starting now!",
                body  = "${session.learner?.name} is waiting for your $skill session.",
                data  = joinData)
        }
        session.learner?.fcmToken?.let { token ->
            pushNotificationService.sendNotification(deviceToken = token,
                title = "📹 Session starting now!",
                body  = "Your $skill session with ${session.teacher?.name} is live!",
                data  = joinData)
        }
    }

    /**
     * Runs every minute. Checks for no-shows 10 minutes after session started.
     * Penalizes the absent party: refunds learner credits if teacher no-show,
     * deducts penalty credits if learner no-show.
     */
    @Scheduled(fixedRate = 60_000)
    fun handleNoShows() {
        try { handleNoShowsInternal() } catch (e: Exception) { println("handleNoShows error: ${e.message}") }
    }

    @Transactional
    fun handleNoShowsInternal() {
        val penaltyTime = LocalDateTime.now().minusMinutes(10)

        // Student no-show: learner never joined but teacher was there
        val studentNoShows = sessionRepository.findInProgressStudentNoShow(penaltyTime)
        for (session in studentNoShows) {
            if (session.teacherJoinedAt != null) {
                // Teacher showed up, student didn't → student penalized
                session.status = SessionStatus.STUDENT_NO_SHOW
                // Teacher still gets paid for their time
                session.teacher?.let { teacher ->
                    teacher.credits += session.creditsHeld
                    userRepository.save(teacher)
                }
                session.learner?.let { learner ->
                    learner.heldCredits -= session.creditsHeld
                    learner.noShowCount = learner.noShowCount + 1
                    // "Showing up on time" is one of the concrete signals the
                    // reliability score is meant to track — a confirmed
                    // no-show is the clearest possible negative case for it.
                    learner.reliabilityScore = (learner.reliabilityScore - 10).coerceAtLeast(0)
                    userRepository.save(learner)
                }
                sessionRepository.save(session)
                session.teacher?.fcmToken?.let { token ->
                    pushNotificationService.sendNotification(
                        deviceToken = token,
                        title = "Session completed",
                        body  = "Student didn't join. Credits have been transferred to you.",
                        data  = mapOf("type" to "NO_SHOW", "sessionId" to session.id.toString())
                    )
                }
                session.learner?.fcmToken?.let { token ->
                    pushNotificationService.sendNotification(
                        deviceToken = token,
                        title = "Session missed",
                        body  = "You didn't join your session. Credits were not refunded.",
                        data  = mapOf("type" to "NO_SHOW", "sessionId" to session.id.toString())
                    )
                }
            }
        }

        // Teacher no-show: teacher never joined
        val teacherNoShows = sessionRepository.findInProgressTeacherNoShow(penaltyTime)
        for (session in teacherNoShows) {
            if (session.studentJoinedAt != null || LocalDateTime.now().isAfter(
                    session.actualStartTime!!.plusMinutes(10))) {
                // Teacher didn't show → full refund to learner
                session.status = SessionStatus.TEACHER_NO_SHOW
                session.learner?.let { learner ->
                    learner.heldCredits -= session.creditsHeld
                    learner.credits += session.creditsHeld  // full refund
                    userRepository.save(learner)
                }
                session.teacher?.let { teacher ->
                    // Same signal as above, applied to the teacher for
                    // failing to show up to a session they confirmed.
                    teacher.reliabilityScore = (teacher.reliabilityScore - 10).coerceAtLeast(0)
                    userRepository.save(teacher)
                }
                sessionRepository.save(session)
                session.learner?.fcmToken?.let { token ->
                    pushNotificationService.sendNotification(
                        deviceToken = token,
                        title = "Session cancelled — full refund",
                        body  = "The teacher didn't join. Your credits have been refunded.",
                        data  = mapOf("type" to "TEACHER_NO_SHOW", "sessionId" to session.id.toString())
                    )
                }
                session.teacher?.fcmToken?.let { token ->
                    pushNotificationService.sendNotification(
                        deviceToken = token,
                        title = "Session missed",
                        body  = "You missed your session. The student received a full refund.",
                        data  = mapOf("type" to "TEACHER_NO_SHOW", "sessionId" to session.id.toString())
                    )
                }
            }
        }
    }

    /**
     * Runs every 5 minutes. Auto-declines PENDING sessions the teacher never responded to
     * within 2 hours, and refunds the learner's credits.
     */
    @Scheduled(fixedRate = 300_000)
    fun autoDeclineExpiredPending() {
        try { autoDeclineExpiredPendingInternal() } catch (e: Exception) { println("autoDecline error: ${e.message}") }
    }

    @Transactional
    fun autoDeclineExpiredPendingInternal() {
        val cutoff = LocalDateTime.now().minusHours(2)
        val expired = sessionRepository.findExpiredPendingSessions(cutoff)
        for (session in expired) {
            session.status = SessionStatus.AUTO_DECLINED
            session.declineReason = "Teacher did not respond within 2 hours"
            session.learner?.let { learner ->
                learner.heldCredits -= session.creditsHeld
                learner.credits += session.creditsHeld
                userRepository.save(learner)
            }
            sessionRepository.save(session)
            session.learner?.fcmToken?.let { token ->
                pushNotificationService.sendNotification(
                    deviceToken = token,
                    title = "Session auto-cancelled",
                    body  = "The teacher didn't respond in time. Full refund issued.",
                    data  = mapOf("type" to "AUTO_DECLINED", "sessionId" to session.id.toString())
                )
            }
        }
    }

    /**
     * Runs every minute. Sends push notification to both teacher and learner
     * 5 minutes before the session starts.
     */
    @Scheduled(fixedRate = 60_000)  // every 60 seconds
    fun sendSessionReminders() {
        val now = LocalDateTime.now()
        val windowStart = now.plusMinutes(4)
        val windowEnd   = now.plusMinutes(6)

        val upcoming = sessionRepository.findConfirmedSessionsInWindow(windowStart, windowEnd)
        for (session in upcoming) {
            val msg = "Your session starts in ~5 minutes! Tap to join."
            val data = mapOf(
                "type"      to "SESSION_REMINDER",
                "sessionId" to session.id.toString(),
                "action"    to "JOIN_SESSION"
            )
            session.teacher?.fcmToken?.let { token ->
                pushNotificationService.sendNotification(
                    deviceToken = token,
                    title       = "⏰ Session starting soon!",
                    body        = msg,
                    data        = data
                )
            }
            session.learner?.fcmToken?.let { token ->
                pushNotificationService.sendNotification(
                    deviceToken = token,
                    title       = "⏰ Session starting soon!",
                    body        = msg,
                    data        = data
                )
            }
        }
    }

    /**
     * Runs every 5 minutes. Catches CONFIRMED sessions that neither party ever
     * joined (so they never became IN_PROGRESS and handleNoShows() never saw them).
     * Once a session's scheduled window is fully over with a grace period, mark it
     * BOTH_NO_SHOW and fully refund the learner — otherwise it sits there forever
     * still showing a "Join Video Call" button long after the date has passed.
     */
    @Scheduled(fixedRate = 300_000)
    fun expireUnjoinedConfirmedSessions() {
        try { expireUnjoinedConfirmedSessionsInternal() } catch (e: Exception) { println("expireUnjoinedConfirmedSessions error: ${e.message}") }
    }

    @Transactional
    fun expireUnjoinedConfirmedSessionsInternal() {
        val now = LocalDateTime.now()
        val graceMinutes = 15L
        // Query broadly (scheduledAt in the past), then filter precisely using each
        // session's own duration + grace period, same style as the conflict check above.
        val candidates = sessionRepository.findUnjoinedConfirmedSessionsBefore(now)
        for (session in candidates) {
            val scheduledEnd = session.scheduledAt!!.plusMinutes(session.durationMinutes.toLong())
            if (now.isBefore(scheduledEnd.plusMinutes(graceMinutes))) continue

            session.status = SessionStatus.BOTH_NO_SHOW
            session.learner?.let { learner ->
                learner.heldCredits -= session.creditsHeld
                learner.credits += session.creditsHeld  // full refund — nobody showed up
                userRepository.save(learner)
            }
            sessionRepository.save(session)
            session.teacher?.fcmToken?.let { token ->
                pushNotificationService.sendNotification(
                    deviceToken = token,
                    title = "Session expired",
                    body  = "Neither of you joined in time, so this session was closed automatically.",
                    data  = mapOf("type" to "BOTH_NO_SHOW", "sessionId" to session.id.toString())
                )
            }
            session.learner?.fcmToken?.let { token ->
                pushNotificationService.sendNotification(
                    deviceToken = token,
                    title = "Session expired — refunded",
                    body  = "Neither of you joined in time. Your credits have been refunded.",
                    data  = mapOf("type" to "BOTH_NO_SHOW", "sessionId" to session.id.toString())
                )
            }
        }
    }

    private fun mapToSessionResponse(session: Session): SessionResponse {
        val start = session.scheduledAt ?: LocalDateTime.now().plusDays(1)
        val end   = start.plusMinutes(session.durationMinutes.toLong())
        // Use system default zone so the ISO string matches what the learner booked
        val zone  = java.time.ZoneId.systemDefault()
        return SessionResponse(
            id               = session.id,
            teacherId        = session.teacher!!.id,
            studentId        = session.learner!!.id,
            skillId          = session.skill!!.id,
            teacherName      = session.teacher!!.name,
            studentName      = session.learner!!.name,
            teacherAvatarUrl = session.teacher!!.avatar,
            studentAvatarUrl = session.learner!!.avatar,
            skillName        = session.skill!!.name,
            scheduledStartTime = start.atZone(zone).toInstant().toString(),
            scheduledEndTime   = end.atZone(zone).toInstant().toString(),
            actualStartTime    = session.actualStartTime?.atZone(zone)?.toInstant()?.toString(),
            actualEndTime      = session.actualEndTime?.atZone(zone)?.toInstant()?.toString(),
            status           = session.status,
            creditsAmount    = session.creditsUsed.toInt(),
            channelName      = session.videoCallLink,
            videoCallLink    = session.videoCallLink,
            teacherRating    = session.teacherRating,
            studentRating    = session.studentRating,
            teacherFeedback  = session.teacherFeedback,
            studentFeedback  = session.studentFeedback,
            createdAt        = session.createdAt.atZone(zone).toInstant().toString(),
            updatedAt        = session.updatedAt.atZone(zone).toInstant().toString(),
            learnerAcknowledgedBooking = session.learnerAcknowledgedBooking
        )
    }
}