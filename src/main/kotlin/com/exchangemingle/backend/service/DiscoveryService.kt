package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.SkillNotFoundException
import com.exchangemingle.backend.model.SessionStatus
import com.exchangemingle.backend.model.SkillRole
import com.exchangemingle.backend.repository.SessionRequestRepository
import com.exchangemingle.backend.repository.SessionRepository
import com.exchangemingle.backend.repository.SkillRepository
import com.exchangemingle.backend.repository.UserRepository
import com.exchangemingle.backend.repository.UserSkillRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class DiscoveryService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val skillRepository: SkillRepository,
    private val sessionRequestRepository: SessionRequestRepository,
    private val userSkillRepository: UserSkillRepository,
    private val userSkillService: UserSkillService
) {

    @Cacheable(value = ["discovery-teachers"], key = "#skillId + ':' + #page + ':' + #size", cacheManager = "redisCacheManager")
    fun findTeachers(skillId: Long? = null, page: Int = 0, size: Int = 20): PagedTeacherCardResponse {
        // Use UserSkill as source of truth: any active TEACHER skill = discoverable
        val teacherUserSkills = if (skillId != null) {
            val skill = skillRepository.findById(skillId)
                .orElseThrow { SkillNotFoundException("Skill not found: \$skillId") }
            userSkillRepository.findBySkillAndRoleAndIsActive(skill, SkillRole.TEACHER, true)
        } else {
            userSkillRepository.findByRoleAndIsActive(SkillRole.TEACHER, true)
        }

        // Deduplicate teachers (one teacher can teach multiple skills)
        val distinctTeacherIds = teacherUserSkills.mapNotNull { it.user?.id }.distinct()

        if (distinctTeacherIds.isEmpty()) {
            return PagedTeacherCardResponse(
                content = emptyList(), page = page, size = size,
                totalElements = 0, totalPages = 0, isLast = true
            )
        }

        val scored = distinctTeacherIds.mapNotNull { teacherId ->
            val teacher = userRepository.findById(teacherId).orElse(null) ?: return@mapNotNull null

            val avgRating = sessionRepository.getAverageRatingForTeacher(teacher) ?: 0.0
            val totalTaught = sessionRepository.countByTeacherAndStatus(teacher, SessionStatus.COMPLETED)
            val primarySkill = teacherUserSkills.firstOrNull { it.user?.id == teacherId }
            val skillName = primarySkill?.skill?.name
            val skillId   = primarySkill?.skill?.id
            val hourlyCredits = primarySkill?.hourlyCredits

            val ratingScore    = (avgRating / 5.0) * 0.40
            val experienceScore = (minOf(totalTaught, 50L).toDouble() / 50.0) * 0.30
            val profileScore   = (if (teacher.bio != null && teacher.avatar != null) 1.0 else 0.0) * 0.10
            val recentSessions = sessionRepository.countRecentCompletedByTeacher(teacher, LocalDateTime.now().minusDays(30))
            val recencyScore   = (if (recentSessions > 0) 1.0 else 0.0) * 0.20
            val totalScore     = ratingScore + experienceScore + recencyScore + profileScore

            TeacherCard(
                id = teacher.id,
                name = teacher.name,
                avatar = teacher.avatar,
                averageRating = if (avgRating > 0) avgRating else null,
                totalSessionsTaught = totalTaught,
                score = totalScore,
                bio = teacher.bio,
                skillName = skillName,
                hourlyCredits = hourlyCredits,
                skillId = skillId
            )
        }.sortedByDescending { it.score }

        val totalElements = scored.size.toLong()
        val totalPages = ((totalElements + size - 1) / size).toInt()
        val fromIndex = page * size
        val toIndex = minOf(fromIndex + size, scored.size)
        val paged = if (fromIndex < scored.size) scored.subList(fromIndex, toIndex) else emptyList()

        return PagedTeacherCardResponse(
            content = paged, page = page, size = size,
            totalElements = totalElements, totalPages = totalPages,
            isLast = page >= totalPages - 1
        )
    }

    @Cacheable(value = ["discovery-requests"], key = "#skillId + ':' + #page + ':' + #size", cacheManager = "redisCacheManager")
    fun findOpenRequests(skillId: Long? = null, page: Int = 0, size: Int = 20): PagedOpenRequestResponse {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())

        val requestsPage = if (skillId != null) {
            val skill = skillRepository.findById(skillId)
                .orElseThrow { SkillNotFoundException("Skill not found: $skillId") }
            sessionRequestRepository.findByStatusAndSkill(
                com.exchangemingle.backend.model.SessionRequestStatus.OPEN, skill, pageable
            )
        } else {
            sessionRequestRepository.findByStatus(
                com.exchangemingle.backend.model.SessionRequestStatus.OPEN, pageable
            )
        }

        return PagedOpenRequestResponse(
            content = requestsPage.content.map { sr ->
                OpenRequestCard(
                    id = sr.id,
                    learner = UserSummary(sr.learner!!.id, sr.learner!!.name, sr.learner!!.avatar),
                    skillName = sr.skill!!.name,
                    skillCategory = sr.skill!!.category,
                    durationMinutes = sr.durationMinutes,
                    message = sr.message,
                    createdAt = sr.createdAt,
                    viewCount = sr.viewCount,
                    interestCount = sr.interestCount
                )
            },
            page = requestsPage.number, size = requestsPage.size,
            totalElements = requestsPage.totalElements, totalPages = requestsPage.totalPages,
            isLast = requestsPage.isLast
        )
    }

    fun findTeacherProfiles(skillId: Long, page: Int = 0, size: Int = 20): com.exchangemingle.backend.dto.PagedTeacherProfileResponse {
        return userSkillService.findTeachersBySkill(skillId, page, size)
    }

    fun getRecommendations(userId: Long): List<TeacherCard> {
        val user = userRepository.findById(userId).orElse(null) ?: return emptyList()
        val skillIds = sessionRepository.findSkillIdsByLearner(userId)

        val allTeachers = mutableListOf<TeacherCard>()

        val targetSkillIds = if (skillIds.isNotEmpty()) skillIds else {
            skillRepository.findAll(PageRequest.of(0, 3)).content.map { it.id }
        }

        for (sid in targetSkillIds) {
            val result = findTeachers(sid, page = 0, size = 3)
            allTeachers.addAll(result.content)
        }

        return allTeachers
            .groupBy { it.id }
            .map { (_, cards) -> cards.maxByOrNull { it.score }!! }
            .sortedByDescending { it.score }
            .take(5)
    }
}