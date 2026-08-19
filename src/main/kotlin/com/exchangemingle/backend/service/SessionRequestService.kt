package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.*
import com.exchangemingle.backend.model.SessionRequest
import com.exchangemingle.backend.model.SessionRequestStatus
import com.exchangemingle.backend.model.SessionRequestOffer
import com.exchangemingle.backend.model.OfferStatus
import com.exchangemingle.backend.repository.SessionRequestRepository
import com.exchangemingle.backend.repository.SessionRequestOfferRepository
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
    private val sessionRequestOfferRepository: SessionRequestOfferRepository,
    private val userRepository: UserRepository,
    private val skillRepository: SkillRepository,
    private val pushNotificationService: PushNotificationService,
    private val userSkillRepository: UserSkillRepository,
    private val sessionService: SessionService
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

    /**
     * A teacher offers to teach this request. Any number of teachers can do
     * this concurrently — the request stays OPEN (still visible to other
     * teachers) until the learner actually chooses one via chooseOffer().
     * This replaced the old exclusive "first to accept wins" behavior, which
     * locked the request to a single teacher immediately and hid it from
     * everyone else even if that teacher never went on to schedule it.
     */
    @Transactional
    fun acceptRequest(requestId: Long, teacherId: Long): SessionRequestResponse {
        val request = sessionRequestRepository.findById(requestId)
            .orElseThrow { SessionNotFoundException("Request not found: $requestId") }

        if (request.status != SessionRequestStatus.OPEN) {
            throw InvalidSessionOperationException("This request is no longer open to new offers")
        }

        if (request.learner?.id == teacherId) {
            throw SelfBookingException("You cannot accept your own request")
        }

        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found: $teacherId") }

        val existing = sessionRequestOfferRepository.findBySessionRequestAndTeacherId(request, teacherId)
        if (existing != null && existing.status == OfferStatus.PENDING) {
            // Idempotent: teacher already has a live offer here, just return current state.
            return mapToResponse(request)
        }

        if (existing != null) {
            // Re-offering after having withdrawn/been declined earlier.
            existing.status = OfferStatus.PENDING
            existing.chosenAt = null
            sessionRequestOfferRepository.save(existing)
        } else {
            sessionRequestOfferRepository.save(
                SessionRequestOffer(sessionRequest = request, teacher = teacher, status = OfferStatus.PENDING)
            )
        }

        request.learner?.fcmToken?.let { token ->
            pushNotificationService.sendSessionAcceptedNotification(
                deviceToken = token,
                requestId = requestId,
                teacherName = teacher.name,
                skillName = request.skill?.name ?: "the skill"
            )
        }

        return mapToResponse(request)
    }

    /** All PENDING offers a learner can choose between for their own request. */
    @Transactional(readOnly = true)
    fun getOffers(requestId: Long, learnerId: Long): List<RequestOfferResponse> {
        val request = sessionRequestRepository.findById(requestId)
            .orElseThrow { SessionNotFoundException("Request not found: $requestId") }
        if (request.learner?.id != learnerId) {
            throw InvalidSessionOperationException("Only the learner who posted this request can view its offers")
        }
        return sessionRequestOfferRepository.findBySessionRequest(request)
            .filter { it.status == OfferStatus.PENDING }
            .map {
                RequestOfferResponse(
                    id = it.id,
                    requestId = requestId,
                    teacher = UserSummary(it.teacher!!.id, it.teacher!!.name, it.teacher!!.avatar),
                    status = it.status.name,
                    createdAt = it.createdAt,
                    chosenAt = it.chosenAt
                )
            }
    }

    /**
     * Learner picks one teacher out of possibly several who offered. Every
     * other PENDING offer on the request is auto-declined (with a
     * notification) and the request moves to MATCHED, waiting on the chosen
     * teacher to actually pick a date/time.
     */
    @Transactional
    fun chooseOffer(requestId: Long, learnerId: Long, offerId: Long): SessionRequestResponse {
        val request = sessionRequestRepository.findById(requestId)
            .orElseThrow { SessionNotFoundException("Request not found: $requestId") }

        if (request.learner?.id != learnerId) {
            throw InvalidSessionOperationException("Only the learner who posted this request can choose a teacher")
        }
        if (request.status != SessionRequestStatus.OPEN) {
            throw InvalidSessionOperationException("A teacher has already been chosen for this request")
        }

        val chosen = sessionRequestOfferRepository.findById(offerId)
            .orElseThrow { SessionRequestNotFoundException("Offer not found: $offerId") }
        if (chosen.sessionRequest?.id != requestId || chosen.status != OfferStatus.PENDING) {
            throw InvalidSessionOperationException("This offer is no longer available")
        }

        val now = LocalDateTime.now()
        chosen.status = OfferStatus.CHOSEN
        chosen.chosenAt = now
        sessionRequestOfferRepository.save(chosen)

        val chosenTeacher = chosen.teacher

        // Decline everyone else who offered on this same request.
        sessionRequestOfferRepository.findBySessionRequest(request)
            .filter { it.status == OfferStatus.PENDING && it.id != chosen.id }
            .forEach { other ->
                other.status = OfferStatus.DECLINED
                sessionRequestOfferRepository.save(other)
                other.teacher?.fcmToken?.let { token ->
                    pushNotificationService.sendOfferDeclinedNotification(
                        deviceToken = token,
                        requestId = requestId,
                        skillName = request.skill?.name ?: "the skill"
                    )
                }
            }

        request.status = SessionRequestStatus.MATCHED
        request.acceptedBy = chosenTeacher
        request.acceptedAt = now
        val updated = sessionRequestRepository.save(request)

        chosenTeacher?.fcmToken?.let { token ->
            pushNotificationService.sendOfferChosenNotification(
                deviceToken = token,
                requestId = requestId,
                learnerName = request.learner?.name ?: "The learner",
                skillName = request.skill?.name ?: "the skill"
            )
        }

        return mapToResponse(updated)
    }

    /**
     * Requests where this teacher was chosen but never came back to pick a
     * date/time — the reminder feed that fixes requests silently vanishing
     * once "accepted" instead of nagging the teacher to finish scheduling.
     */
    @Transactional(readOnly = true)
    fun getMyChosenUnscheduled(teacherId: Long): List<ChosenUnscheduledRequestResponse> {
        return sessionRequestOfferRepository.findChosenUnscheduledByTeacher(teacherId).map { offer ->
            val sr = offer.sessionRequest!!
            ChosenUnscheduledRequestResponse(
                offerId = offer.id,
                requestId = sr.id,
                learner = UserSummary(sr.learner!!.id, sr.learner!!.name, sr.learner!!.avatar),
                skillName = sr.skill?.name ?: "",
                durationMinutes = sr.durationMinutes,
                chosenAt = offer.chosenAt
            )
        }
    }

    /**
     * Lets a teacher's own request-detail screen figure out, on load, whether
     * *this* teacher already has an offer here and what state it's in —
     * instead of relying on screen-local state that resets to "not accepted
     * yet" every time the teacher navigates away and back (the actual cause
     * of accepted requests seeming to disappear).
     */
    @Transactional(readOnly = true)
    fun getMyOfferForRequest(requestId: Long, teacherId: Long): MyOfferForRequestResponse {
        val request = sessionRequestRepository.findById(requestId)
            .orElseThrow { SessionNotFoundException("Request not found: $requestId") }
        val offer = sessionRequestOfferRepository.findBySessionRequestAndTeacherId(request, teacherId)
        return MyOfferForRequestResponse(
            offerExists = offer != null && offer.status != OfferStatus.WITHDRAWN,
            offerStatus = offer?.status?.name,
            requestStatus = request.status,
            scheduledSessionId = request.scheduledSessionId
        )
    }

    /**
     * Teacher picks a date/time for a request they've already accepted. This is
     * the step that was missing: accepting only claimed the request, it never
     * created an actual bookable Session. This creates one (CONFIRMED — the
     * teacher already committed by accepting) and links it back to the request.
     */
    @Transactional
    fun scheduleAcceptedRequest(requestId: Long, teacherId: Long, scheduledAt: LocalDateTime): SessionResponse {
        val request = sessionRequestRepository.findById(requestId)
            .orElseThrow { SessionNotFoundException("Request not found: $requestId") }

        if (request.acceptedBy?.id != teacherId) {
            throw InvalidSessionOperationException("Only the teacher chosen for this request can schedule it")
        }

        if (request.status != SessionRequestStatus.MATCHED) {
            throw InvalidSessionOperationException("Only a matched request can be scheduled")
        }

        if (request.scheduledSessionId != null) {
            throw InvalidSessionOperationException("This request has already been scheduled")
        }

        val learnerId = request.learner?.id
            ?: throw UserNotFoundException("Learner not found for request $requestId")
        val skillId = request.skill?.id
            ?: throw SkillNotFoundException("Skill not found for request $requestId")

        val sessionResponse = sessionService.createConfirmedSessionForAcceptedRequest(
            learnerId = learnerId,
            teacherId = teacherId,
            skillId = skillId,
            durationMinutes = request.durationMinutes,
            scheduledAt = scheduledAt
        )

        request.scheduledSessionId = sessionResponse.id
        request.status = SessionRequestStatus.SCHEDULED
        sessionRequestRepository.save(request)

        sessionRequestOfferRepository.findBySessionRequestAndTeacherId(request, teacherId)?.let { offer ->
            offer.scheduledSessionId = sessionResponse.id
            sessionRequestOfferRepository.save(offer)
        }

        return sessionResponse
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

    // Same safety net as DiscoveryService.findOpenRequests — the JOIN FETCH query
    // in findByLearner is the actual fix, this just guards future field additions.
    @Transactional(readOnly = true)
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

    /**
     * Runs once a day. Hard-deletes EXPIRED/CANCELLED requests that have sat
     * untouched for 30+ days. expireOldRequests() above already makes these
     * invisible on Explore after 7 days (OPEN → EXPIRED); this just stops
     * that history from growing the table forever. 30 days gives plenty of
     * room to look up a recent request (e.g. for a support question) before
     * it's gone for good — ACCEPTED requests are never touched here.
     */
    @Transactional
    @Scheduled(cron = "0 30 3 * * *")
    fun purgeOldRequests() {
        val cutoff = LocalDateTime.now().minusDays(30)
        val deletedCount = sessionRequestRepository.deleteOldExpiredOrCancelled(cutoff)
        if (deletedCount > 0) {
            logger.info("Purged $deletedCount old expired/cancelled session requests")
        }
    }

    private fun mapToResponse(sr: SessionRequest): SessionRequestResponse {
        val pendingCount = sessionRequestOfferRepository.findBySessionRequest(sr)
            .count { it.status == OfferStatus.PENDING }
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
            interestCount = sr.interestCount,
            scheduledSessionId = sr.scheduledSessionId,
            pendingOfferCount = pendingCount
        )
    }
}