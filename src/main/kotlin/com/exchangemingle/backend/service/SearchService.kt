package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.InvalidRequestException
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.SessionStatus
import com.exchangemingle.backend.repository.*
import java.time.ZoneOffset
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchService(
    private val userRepository: UserRepository,
    private val skillRepository: SkillRepository,
    private val sessionRepository: SessionRepository,
    private val blockedUserRepository: BlockedUserRepository
) {

    @Transactional(readOnly = true)
    fun searchUsers(
        currentUserId: Long,
        request: UserSearchRequest,
        page: Int,
        size: Int
    ): UserSearchResponse {
        val currentUser = userRepository.findById(currentUserId)
            .orElseThrow { UserNotFoundException("User not found") }

        val sort = when (request.sortBy.lowercase()) {
            "name" -> Sort.by(
                if (request.sortDirection.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC,
                "name"
            )
            "reliability" -> Sort.by(
                if (request.sortDirection.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC,
                "reliabilityScore"
            )
            else -> Sort.by(Sort.Direction.DESC, "reliabilityScore")
        }

        val pageable = PageRequest.of(page, size, sort)

        val usersPage = if (!request.skillIds.isNullOrEmpty()) {
            userRepository.searchUsersBySkills(
                query = request.query,
                skillIds = request.skillIds,
                pageable = pageable
            )
        } else {
            userRepository.searchUsers(
                query = request.query,
                hasAvatar = request.hasAvatar,
                isEmailVerified = request.isEmailVerified,
                minReliability = if (request.minRating != null) (request.minRating * 20).toInt() else null,
                pageable = pageable
            )
        }

        val users = usersPage.content
            .filter { it.id != currentUserId }
            .map { user ->
                val isBlocked = if (request.excludeBlocked) {
                    blockedUserRepository.areUsersBlockingEachOther(currentUser, user)
                } else false

                val skills = sessionRepository.findSkillIdsByTeacher(user.id)
                    .mapNotNull { skillId ->
                        skillRepository.findById(skillId).orElse(null)
                    }
                    .map { skill ->
                        SkillResponse(
                            id = skill.id,
                            name = skill.name,
                            category = skill.category,
                            description = skill.description
                        )
                    }

                UserSearchResult(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    bio = user.bio,
                    avatar = user.avatar,
                    credits = user.credits,
                    isEmailVerified = user.isEmailVerified,
                    reliabilityScore = user.reliabilityScore,
                    skills = skills,
                    averageRating = sessionRepository.getAverageRatingForTeacher(user),
                    totalSessionsAsTeacher = sessionRepository.countByTeacher(user),
                    totalSessionsAsLearner = sessionRepository.countByLearner(user),
                    isBlocked = isBlocked
                )
            }
            .filter { !request.excludeBlocked || !it.isBlocked }

        return UserSearchResponse(
            users = users,
            totalResults = users.size,
            page = page,
            size = size,
            totalPages = usersPage.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun searchSkills(
        request: SkillSearchRequest,
        page: Int,
        size: Int
    ): PagedSkillResponse {
        val sort = when (request.sortBy.lowercase()) {
            "category" -> Sort.by(
                if (request.sortDirection.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC,
                "category"
            )
            else -> Sort.by(
                if (request.sortDirection.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC,
                "name"
            )
        }

        val pageable = PageRequest.of(page, size, sort)

        val skillsPage = skillRepository.searchSkills(
            query = request.query,
            category = request.category,
            pageable = pageable
        )

        return PagedSkillResponse(
            content = skillsPage.content.map { skill ->
                SkillResponse(
                    id = skill.id,
                    name = skill.name,
                    category = skill.category,
                    description = skill.description
                )
            },
            page = skillsPage.number,
            size = skillsPage.size,
            totalElements = skillsPage.totalElements,
            totalPages = skillsPage.totalPages,
            isLast = skillsPage.isLast
        )
    }

    @Transactional(readOnly = true)
    fun searchSessions(
        userId: Long,
        request: SessionSearchRequest,
        page: Int,
        size: Int
    ): PagedSessionResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found") }

        val sort = when (request.sortBy.lowercase()) {
            "createdat" -> Sort.by(
                if (request.sortDirection.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC,
                "createdAt"
            )
            "rating" -> Sort.by(
                if (request.sortDirection.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC,
                "rating"
            )
            else -> Sort.by(
                if (request.sortDirection.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC,
                "scheduledAt"
            )
        }

        val pageable = PageRequest.of(page, size, sort)

        val status = request.status?.let { SessionStatus.valueOf(it.uppercase()) }

        val sessionsPage = when (request.role?.uppercase()) {
            "TEACHER" -> sessionRepository.searchTeacherSessions(
                userId = userId,
                status = status,
                skillId = request.skillId,
                startDate = request.startDate,
                endDate = request.endDate,
                pageable = pageable
            )
            "LEARNER" -> sessionRepository.searchLearnerSessions(
                userId = userId,
                status = status,
                skillId = request.skillId,
                startDate = request.startDate,
                endDate = request.endDate,
                pageable = pageable
            )
            else -> sessionRepository.searchUserSessions(
                userId = userId,
                status = status,
                skillId = request.skillId,
                startDate = request.startDate,
                endDate = request.endDate,
                hasRating = request.hasRating,
                hasFeedback = request.hasFeedback,
                minRating = request.minRating,
                pageable = pageable
            )
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

    @Transactional(readOnly = true)
    fun globalSearch(
        currentUserId: Long,
        request: GlobalSearchRequest,
        page: Int = 0,
        size: Int = 20
    ): GlobalSearchResponse {
        if (request.query.isBlank()) {
            throw InvalidRequestException("Search query cannot be empty")
        }

        val users = if (request.searchType.uppercase() in listOf("ALL", "USERS")) {
            val userSearchRequest = UserSearchRequest(
                query = request.query,
                excludeBlocked = true
            )
            searchUsers(currentUserId, userSearchRequest, page, size).users
        } else emptyList()

        val skills = if (request.searchType.uppercase() in listOf("ALL", "SKILLS")) {
            val skillSearchRequest = SkillSearchRequest(query = request.query)
            searchSkills(skillSearchRequest, page, size).content
        } else emptyList()

        val sessions = if (request.searchType.uppercase() in listOf("ALL", "SESSIONS")) {
            val sessionSearchRequest = SessionSearchRequest()
            searchSessions(currentUserId, sessionSearchRequest, page, size).content
        } else emptyList()

        return GlobalSearchResponse(
            users = users,
            skills = skills,
            sessions = sessions,
            totalResults = users.size + skills.size + sessions.size
        )
    }


    private fun mapToSessionResponse(session: com.exchangemingle.backend.model.Session): SessionResponse {
        val start = session.scheduledAt ?: java.time.LocalDateTime.now().plusDays(1)
        val end   = start.plusMinutes(session.durationMinutes.toLong())
        return SessionResponse(
            id                 = session.id,
            teacherId          = session.teacher!!.id,
            studentId          = session.learner!!.id,
            skillId            = session.skill!!.id,
            teacherName        = session.teacher!!.name,
            studentName        = session.learner!!.name,
            teacherAvatarUrl   = session.teacher!!.avatar,
            studentAvatarUrl   = session.learner!!.avatar,
            skillName          = session.skill!!.name,
            scheduledStartTime = start.toInstant(java.time.ZoneOffset.UTC).toString(),
            scheduledEndTime   = end.toInstant(java.time.ZoneOffset.UTC).toString(),
            actualStartTime    = session.actualStartTime?.toInstant(java.time.ZoneOffset.UTC)?.toString(),
            actualEndTime      = session.actualEndTime?.toInstant(java.time.ZoneOffset.UTC)?.toString(),
            status             = session.status,
            creditsAmount      = session.creditsUsed.toInt(),
            channelName        = session.videoCallLink,
            videoCallLink      = session.videoCallLink,
            teacherRating      = session.teacherRating,
            studentRating      = session.studentRating,
            teacherFeedback    = session.teacherFeedback,
            studentFeedback    = session.studentFeedback,
            createdAt          = session.createdAt.toInstant(java.time.ZoneOffset.UTC).toString(),
            updatedAt          = session.updatedAt.toInstant(java.time.ZoneOffset.UTC).toString()
        )
    }
}