package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.*
import com.exchangemingle.backend.model.SessionRequest
import com.exchangemingle.backend.model.SessionRequestStatus
import com.exchangemingle.backend.repository.SessionRequestRepository
import com.exchangemingle.backend.repository.SkillRepository
import com.exchangemingle.backend.repository.UserSkillRepository
import com.exchangemingle.backend.model.SkillRole
import com.exchangemingle.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

import org.springframework.cache.annotation.CacheEvict

@Service
class SessionRequestService(
    private val sessionRequestRepository: SessionRequestRepository,
    private val userRepository: UserRepository,
    private val skillRepository: SkillRepository,
    private val pushNotificationService: PushNotificationService,
    private val userSkillRepository: UserSkillRepository
) {

    private val logger = LoggerFactory.getLogger(SessionRequestService::class.java)

    @Transactional
    @CacheEvict(value = ["discovery-requests", "open-requests-feed"], allEntries = true, cacheManager = "redisCacheManager")
    fun createRequest(learnerId: Long, dto: CreateSessionRequestDto): SessionRequestResponse {
        val learner = userRepository.findById(learnerId)
            .orElseThrow { UserNotFoundException("User not found: $learnerId") }

        val skill = skillRepository.findById(dto.skillId)
            .orElseThrow { SkillNotFoundException("Skill not found: ${dto.skillId}") }

        // NOTE: No credit check here. Credits are only deducted when a teacher
        // actually books a session. Posting a public request is free.

        val request = SessionRequest(
            learner = learner,
            skill = skill,
            durationMinutes = dto.durationMinutes,
            message = dto.message,
            status = SessionRequestStatus.OPEN
        )

        val saved = sessionRequestRepository.save(request)

        // Notify all active teachers of this skill about the new public request
        val teacherSkills = userSkillRepository.findBySkillAndRoleAndIsActive(skill, SkillRole.TEACHER, true)
        for (ts in teacherSkills) {
            val teacherUser = ts.user ?: continue
            if (teacherUser.id == learnerId) continue  // don't notify self
            teacherUser.fcmToken?.let { token ->
                pushNotificationService.sendNewLearnerRequestNotification(
                    deviceToken = token,
                    requestId = saved.id,
                    learnerName = learner.name,
                    skillName = skill.name
                )
            }
        }

        return mapToResponse(saved)
    }

    @Transactional
    fun acceptRequest(requestId: Long, teacherId: Long): SessionRequestResponse {
        val request = sessionRequestRepository.findById(requestId)
            .orElseThrow { SessionNotFoundException("Request not found: $requestId") }

        if (request.status != SessionRequestStatus.OPEN) {
            throw InvalidSessionOperationException("Only open requests can be accepted")
        }

        if (request.learner?.id == teacherId) {
            throw SelfBookingException("You cannot accept your own request")
        }

        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found: $teacherId") }

        request.status = SessionRequestStatus.ACCEPTED
        request.acceptedBy = teacher
        request.acceptedAt = LocalDateTime.now()

        val updated = sessionRequestRepository.save(request)

        request.learner?.fcmToken?.let { token ->
            pushNotificationService.sendSessionAcceptedNotification(
                deviceToken = token,
                requestId = requestId,
                teacherName = teacher.name,
                skillName = request.skill?.name ?: "the skill"
            )
        }

        return mapToResponse(updated)
    }

    @Transactional
    fun cancelRequest(requestId: Long, userId: Long): SessionRequestResponse {
        val request = sessionRequestRepository.findById(requestId)
            .orElseThrow { SessionNotFoundException("Request not found: $requestId") }

        if (request.learner?.id != userId) {
            throw InvalidSessionOperationException("Only the learner can cancel this request")
        }

        if (request.status != SessionRequestStatus.OPEN) {
            throw InvalidSessionOperationException("Only open requests can be cancelled")
        }

        request.status = SessionRequestStatus.CANCELLED
        val updated = sessionRequestRepository.save(request)
        return mapToResponse(updated)
    }

    fun getMyRequests(userId: Long, page: Int = 0, size: Int = 20): PagedSessionRequestResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val paged = sessionRequestRepository.findByLearner(user, pageable)

        return PagedSessionRequestResponse(
            content = paged.content.map { mapToResponse(it) },
            page = paged.number, size = paged.size,
            totalElements = paged.totalElements, totalPages = paged.totalPages,
            isLast = paged.isLast
        )
    }

    /**
     * Records that a teacher viewed this request (increments viewCount).
     * Silently ignored if the viewer is the learner themselves.
     */
    @Transactional
    fun markViewed(requestId: Long, viewerId: Long) {
        val request = sessionRequestRepository.findById(requestId).orElse(null) ?: return
        if (request.learner?.id == viewerId) return  // don't count self-views
        if (request.status != SessionRequestStatus.OPEN) return
        request.viewCount = request.viewCount + 1
        sessionRequestRepository.save(request)
    }

    /**
     * Records that a teacher expressed interest in this request.
     * Increments interestCount and notifies the learner.
     */
    @Transactional
    fun expressInterest(requestId: Long, teacherId: Long) {
        val request = sessionRequestRepository.findById(requestId)
            .orElseThrow { SessionNotFoundException("Request not found: $requestId") }

        if (request.learner?.id == teacherId) {
            throw SelfBookingException("You cannot express interest in your own request")
        }
        if (request.status != SessionRequestStatus.OPEN) {
            throw InvalidSessionOperationException("Cannot express interest in a non-open request")
        }

        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found: $teacherId") }

        request.interestCount = request.interestCount + 1
        sessionRequestRepository.save(request)

        // Notify the learner
        request.learner?.fcmToken?.let { token ->
            pushNotificationService.sendTeacherInterestedNotification(
                deviceToken = token,
                requestId = requestId,
                teacherName = teacher.name,
                skillName = request.skill?.name ?: "the skill"
            )
        }
    }

    @Transactional
    @Scheduled(cron = "0 0 * * * *")
    fun expireOldRequests() {
        val cutoff = LocalDateTime.now().minusDays(7)
        val now = LocalDateTime.now()
        val expiredCount = sessionRequestRepository.expireOldRequests(cutoff, now)
        if (expiredCount > 0) {
            logger.info("Expired $expiredCount old session requests")
        }
    }

    private fun mapToResponse(sr: SessionRequest): SessionRequestResponse {
        return SessionRequestResponse(
            id = sr.id,
            learner = UserSummary(sr.learner!!.id, sr.learner!!.name, sr.learner!!.avatar),
            skillId = sr.skill!!.id,
            skillName = sr.skill!!.name,
            skillCategory = sr.skill!!.category,
            durationMinutes = sr.durationMinutes,
            message = sr.message,
            status = sr.status,
            acceptedBy = sr.acceptedBy?.let { UserSummary(it.id, it.name, it.avatar) },
            acceptedAt = sr.acceptedAt,
            createdAt = sr.createdAt,
            viewCount = sr.viewCount,
            interestCount = sr.interestCount
        )
    }
}