package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.VideoCallResponse
import com.exchangemingle.backend.dto.VideoCallTokenResponse
import com.exchangemingle.backend.exception.InvalidSessionOperationException
import com.exchangemingle.backend.exception.SessionNotFoundException
import com.exchangemingle.backend.model.SessionStatus
import com.exchangemingle.backend.repository.SessionRepository
import io.livekit.server.AccessToken
import io.livekit.server.RoomJoin
import io.livekit.server.RoomName
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
class VideoCallService(
    private val sessionRepository: SessionRepository,
    private val blockedUserService: BlockedUserService
) {
    private val log = LoggerFactory.getLogger(VideoCallService::class.java)

    @Value("\${livekit.url}")
    private lateinit var livekitUrl: String

    @Value("\${livekit.api-key}")
    private lateinit var apiKey: String

    @Value("\${livekit.api-secret}")
    private lateinit var apiSecret: String

    companion object {
        private const val TOKEN_TTL_SECONDS = 86_400L  // 24 hours
    }

    @Transactional
    fun generateToken(sessionId: Long, userId: Long): VideoCallTokenResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found: $sessionId") }

        if (session.status != SessionStatus.CONFIRMED && session.status != SessionStatus.IN_PROGRESS) {
            throw InvalidSessionOperationException(
                "Session is not ready to join yet. Status: ${session.status}"
            )
        }
        if (session.teacher?.id != userId && session.learner?.id != userId) {
            throw InvalidSessionOperationException("You are not a participant in this session")
        }

        // Even a session confirmed before either side blocked the other
        // shouldn't be joinable now — booking/scheduling/accepting are all
        // blocked going forward, but a session that slipped through before
        // this was added (or before the block itself happened) shouldn't
        // still let two blocked users end up in a call together.
        val teacherIdForBlockCheck = session.teacher?.id
        val learnerIdForBlockCheck = session.learner?.id
        if (teacherIdForBlockCheck != null && learnerIdForBlockCheck != null &&
            blockedUserService.isBlocked(teacherIdForBlockCheck, learnerIdForBlockCheck)) {
            throw InvalidSessionOperationException("This session is no longer available to join.")
        }

        val scheduledStart = session.scheduledAt
        // This window must be enforced regardless of whether the session is
        // still CONFIRMED or has already flipped to IN_PROGRESS (which
        // happens the moment anyone first joins) — previously it only ran
        // for CONFIRMED, so the very first join removed the time check
        // entirely for every join after that, letting a session be rejoined
        // hours later with no limit at all.
        if (scheduledStart != null && (session.status == SessionStatus.CONFIRMED || session.status == SessionStatus.IN_PROGRESS)) {
            enforceJoinWindow(scheduledStart, session.durationMinutes)
        }

        val roomName = if (!session.videoCallLink.isNullOrBlank()) {
            session.videoCallLink!!
        } else {
            "em_$sessionId".also { session.videoCallLink = it }
        }

        if (session.status == SessionStatus.CONFIRMED) {
            session.status = SessionStatus.IN_PROGRESS
            session.actualStartTime = LocalDateTime.now()
        }
        sessionRepository.save(session)

        val isTeacher   = session.teacher?.id == userId
        val identity    = "${if (isTeacher) "teacher" else "learner"}_$userId"
        val displayName = if (isTeacher) session.teacher?.name else session.learner?.name
        val token       = buildToken(roomName, identity, displayName)

        log.info("LiveKit token OK  session={} identity={} room={}", sessionId, identity, roomName)

        return VideoCallTokenResponse(
            token          = token,
            channelName    = roomName,
            wsUrl          = livekitUrl,
            identity       = identity,
            expirationTime = System.currentTimeMillis() / 1000 + TOKEN_TTL_SECONDS
        )
    }

    /**
     * Shared join-time-window check used by both generateToken and
     * startVideoCall. Previously startVideoCall had NO time check at all —
     * it only verified the session's status, so as long as status stayed
     * CONFIRMED/IN_PROGRESS (which, before the completion-flow fix, was
     * effectively forever), this endpoint alone was a way to join a session
     * at any point with zero time restriction, regardless of what
     * generateToken enforced.
     */
    private fun enforceJoinWindow(scheduledStart: LocalDateTime, durationMinutes: Int) {
        val now          = LocalDateTime.now()
        val earliestJoin = scheduledStart.minusMinutes(15)
        // Grace period after the session's scheduled end before a NEW join
        // (i.e. a new LiveKit token) is refused. This used to be a flat
        // +20 minutes on top of the full duration — for a 5-minute session
        // that's a 25-minute-long window to join, so a learner could open
        // the call again a long while after the real session was over, the
        // teacher (rightly) wouldn't be there, and the no-show job would
        // refund the learner on top of credits the teacher already earned
        // for actually holding the session. A flat 5-minute grace still
        // covers a session genuinely running a few minutes long without
        // leaving the door open long after it's really finished.
        val latestJoin   = scheduledStart.plusMinutes(durationMinutes.toLong() + 5)
        if (now.isBefore(earliestJoin)) {
            val mins = Duration.between(now, scheduledStart).toMinutes()
            throw InvalidSessionOperationException(
                "Session starts in $mins minute(s). You can join up to 15 minutes early."
            )
        }
        if (now.isAfter(latestJoin)) {
            throw InvalidSessionOperationException(
                "This session has ended. The time window to join has passed."
            )
        }
    }

    @Transactional
    fun startVideoCall(sessionId: Long, userId: Long): VideoCallResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found: $sessionId") }

        if (session.status != SessionStatus.CONFIRMED && session.status != SessionStatus.IN_PROGRESS) {
            throw InvalidSessionOperationException("Session must be CONFIRMED or IN_PROGRESS")
        }
        if (session.teacher?.id != userId && session.learner?.id != userId) {
            throw InvalidSessionOperationException("You are not a participant in this session")
        }
        val teacherId2 = session.teacher?.id
        val learnerId2 = session.learner?.id
        if (teacherId2 != null && learnerId2 != null && blockedUserService.isBlocked(teacherId2, learnerId2)) {
            throw InvalidSessionOperationException("This session is no longer available to join.")
        }
        session.scheduledAt?.let { enforceJoinWindow(it, session.durationMinutes) }

        val roomName = if (!session.videoCallLink.isNullOrBlank()) {
            session.videoCallLink!!
        } else {
            "em_$sessionId".also {
                session.videoCallLink = it
                sessionRepository.save(session)
            }
        }

        val teacherId    = session.teacher!!.id
        val learnerId    = session.learner!!.id
        val teacherToken = buildToken(roomName, "teacher_$teacherId", session.teacher?.name)
        val learnerToken = buildToken(roomName, "learner_$learnerId", session.learner?.name)

        return VideoCallResponse(
            callId       = UUID.randomUUID().toString(),
            channelName  = roomName,
            token        = if (userId == teacherId) teacherToken else learnerToken,
            wsUrl        = livekitUrl,
            sessionId    = sessionId,
            teacherToken = teacherToken,
            learnerToken = learnerToken
        )
    }

    private fun buildToken(roomName: String, identity: String, displayName: String?): String {
        val token = AccessToken(apiKey, apiSecret)
        token.identity = identity
        if (!displayName.isNullOrBlank()) token.name = displayName
        token.ttl = TOKEN_TTL_SECONDS
        token.addGrants(RoomJoin(true), RoomName(roomName))
        return token.toJwt()
    }
}