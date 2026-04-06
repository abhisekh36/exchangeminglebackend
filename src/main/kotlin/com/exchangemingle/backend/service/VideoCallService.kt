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
    private val sessionRepository: SessionRepository
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

        val scheduledStart = session.scheduledAt
        if (scheduledStart != null && session.status == SessionStatus.CONFIRMED) {
            val now          = LocalDateTime.now()
            val earliestJoin = scheduledStart.minusMinutes(15)
            val latestJoin   = scheduledStart.plusMinutes(session.durationMinutes.toLong() + 20)
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