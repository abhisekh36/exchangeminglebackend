package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.*
import com.exchangemingle.backend.model.*
import com.exchangemingle.backend.repository.*
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserSkillService(
    private val userSkillRepository: UserSkillRepository,
    private val userRepository: UserRepository,
    private val skillRepository: SkillRepository,
    private val sessionRepository: SessionRepository,
    private val teacherAvailabilityRepository: TeacherAvailabilityRepository
) {

    private val logger = LoggerFactory.getLogger(UserSkillService::class.java)

    @Transactional
    @CacheEvict(value = ["discovery-teachers"], allEntries = true, cacheManager = "redisCacheManager")
    fun addUserSkill(userId: Long, request: AddUserSkillRequest): UserSkillResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found") }

        val skill = skillRepository.findById(request.skillId)
            .orElseThrow { SkillNotFoundException("Skill not found") }

        val role = SkillRole.valueOf(request.role.uppercase())

        // Check if already exists AND is active (soft-deleted skills can be re-added)
        val existingSkill = userSkillRepository.findByUserAndSkillAndRole(user, skill, role)
        if (existingSkill.isPresent) {
            val existing = existingSkill.get()
            if (existing.isActive) {
                // Still active - truly a duplicate, redirect to edit
                throw InvalidRequestException("You already have this skill with role ${request.role}")
            } else {
                // Was soft-deleted - reactivate instead of creating new
                existing.isActive = true
                request.proficiencyLevel?.let { existing.proficiencyLevel = it }
                request.hourlyCredits?.let { existing.hourlyCredits = it }
                request.yearsOfExperience?.let { existing.yearsOfExperience = it }
                request.teachingBio?.let { existing.teachingBio = it }
                request.proofUrl?.let { existing.proofUrl = it }
                val reactivated = userSkillRepository.save(existing)
                return mapToResponse(reactivated)
            }
        }

        // Validate TEACHER role requirements
        if (role == SkillRole.TEACHER) {
            if (request.proficiencyLevel == null) {
                throw InvalidRequestException("Proficiency level is required for teaching skills")
            }
            if (request.hourlyCredits == null || request.hourlyCredits <= 0) {
                throw InvalidRequestException("Valid hourly credits are required for teaching skills")
            }
        }

        val userSkill = UserSkill(
            user = user,
            skill = skill,
            role = role,
            proficiencyLevel = request.proficiencyLevel,
            hourlyCredits = request.hourlyCredits,
            yearsOfExperience = request.yearsOfExperience,
            teachingBio = request.teachingBio,
            proofUrl = request.proofUrl,
            isActive = true
        )

        val saved = userSkillRepository.save(userSkill)
        return mapToResponse(saved)
    }

    @Transactional
    @CacheEvict(value = ["discovery-teachers"], allEntries = true, cacheManager = "redisCacheManager")
    fun updateUserSkill(userId: Long, userSkillId: Long, request: UpdateUserSkillRequest): UserSkillResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found") }

        val userSkill = userSkillRepository.findById(userSkillId)
            .orElseThrow { SkillNotFoundException("User skill not found") }

        if (userSkill.user?.id != userId) {
            throw InvalidRequestException("You can only update your own skills")
        }

        // Only update if teaching
        if (userSkill.role == SkillRole.TEACHER) {
            request.proficiencyLevel?.let { userSkill.proficiencyLevel = it }
            request.hourlyCredits?.let {
                if (it <= 0) throw InvalidRequestException("Hourly credits must be positive")
                userSkill.hourlyCredits = it
            }
            request.yearsOfExperience?.let { userSkill.yearsOfExperience = it }
            request.teachingBio?.let { userSkill.teachingBio = it }
            request.proofUrl?.let { userSkill.proofUrl = it }
        }

        request.isActive?.let { userSkill.isActive = it }

        val updated = userSkillRepository.save(userSkill)
        return mapToResponse(updated)
    }

    @Transactional
    // A deleted (or edited) skill wasn't evicting the discovery cache at all,
    // so the Explore tab could keep serving a stale cached page — including
    // one built while a since-deleted skill was still active — until some
    // unrelated add() elsewhere happened to clear the whole cache region.
    // Deleting and editing both need to invalidate it themselves.
    @CacheEvict(value = ["discovery-teachers"], allEntries = true, cacheManager = "redisCacheManager")
    fun removeUserSkill(userId: Long, userSkillId: Long) {
        val userSkill = userSkillRepository.findById(userSkillId)
            .orElseThrow { SkillNotFoundException("User skill not found") }

        if (userSkill.user?.id != userId) {
            throw InvalidRequestException("You can only remove your own skills")
        }

        // Soft delete
        userSkill.isActive = false
        userSkillRepository.save(userSkill)
    }

    fun getUserSkills(userId: Long): UserSkillsSummaryResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found") }

        val teachingSkills = userSkillRepository.findByUserAndRoleAndIsActive(user, SkillRole.TEACHER, true)
            .map { mapToResponse(it) }

        val learningSkills = userSkillRepository.findByUserAndRoleAndIsActive(user, SkillRole.LEARNER, true)
            .map { mapToResponse(it) }

        return UserSkillsSummaryResponse(
            teachingSkills = teachingSkills,
            learningSkills = learningSkills,
            totalTeachingSkills = teachingSkills.size,
            totalLearningSkills = learningSkills.size
        )
    }

    fun getTeachingSkills(userId: Long): List<UserSkillResponse> {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found") }

        return userSkillRepository.findByUserAndRoleAndIsActive(user, SkillRole.TEACHER, true)
            .map { mapToResponse(it) }
    }

    fun getLearningSkills(userId: Long): List<UserSkillResponse> {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found") }

        return userSkillRepository.findByUserAndRoleAndIsActive(user, SkillRole.LEARNER, true)
            .map { mapToResponse(it) }
    }

    fun findTeachersBySkill(skillId: Long, page: Int, size: Int): PagedTeacherProfileResponse {
        val skill = skillRepository.findById(skillId)
            .orElseThrow { SkillNotFoundException("Skill not found") }

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reliabilityScore"))
        val teachersPage = userSkillRepository.findTeachersBySkillId(skillId, pageable)

        val teachers = teachersPage.content.map { userSkill ->
            val teacher = userSkill.user!!
            val averageRating = sessionRepository.getAverageRatingForTeacher(teacher)
            val totalSessions = sessionRepository.countByTeacherAndStatus(teacher, SessionStatus.COMPLETED)

            TeacherProfileResponse(
                id = userSkill.id,
                userId = teacher.id,
                userName = teacher.name,
                userAvatar = teacher.avatar,
                skill = SkillResponse(
                    id = userSkill.skill!!.id,
                    name = userSkill.skill!!.name,
                    category = userSkill.skill!!.category,
                    description = userSkill.skill!!.description
                ),
                proficiencyLevel = userSkill.proficiencyLevel,
                hourlyCredits = userSkill.hourlyCredits,
                yearsOfExperience = userSkill.yearsOfExperience,
                teachingBio = userSkill.teachingBio,
                averageRating = averageRating,
                totalSessionsTaught = totalSessions,
                reliabilityScore = teacher.reliabilityScore
            )
        }

        return PagedTeacherProfileResponse(
            content = teachers,
            page = teachersPage.number,
            size = teachersPage.size,
            totalElements = teachersPage.totalElements,
            totalPages = teachersPage.totalPages,
            isLast = teachersPage.isLast
        )
    }

    // Validation for session booking
    fun validateSessionRoles(studentId: Long, teacherId: Long, skillId: Long) {
        val student = userRepository.findById(studentId)
            .orElseThrow { UserNotFoundException("Student not found") }

        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found") }

        val skill = skillRepository.findById(skillId)
            .orElseThrow { SkillNotFoundException("Skill not found") }

        // Rule 1: Can't book yourself
        if (studentId == teacherId) {
            throw InvalidRequestException("You cannot book a session with yourself")
        }

        // Rule 2: Student must be learning this skill
        if (!userSkillRepository.isUserLearningSkill(student, skill)) {
            throw InvalidRequestException("You must add this skill to your learning list before booking")
        }

        // Rule 3: Teacher must be teaching this skill
        if (!userSkillRepository.isUserTeachingSkill(teacher, skill)) {
            throw InvalidRequestException("This user is not teaching this skill")
        }

        // Rule 4: Student can't teach the same skill they're trying to learn
        if (userSkillRepository.isUserTeachingSkill(student, skill)) {
            throw InvalidRequestException("You are already teaching this skill. Cannot book as a student.")
        }
    }

    private fun mapToResponse(userSkill: UserSkill): UserSkillResponse {
        return UserSkillResponse(
            id = userSkill.id,
            skill = SkillResponse(
                id = userSkill.skill!!.id,
                name = userSkill.skill!!.name,
                category = userSkill.skill!!.category,
                description = userSkill.skill!!.description
            ),
            role = userSkill.role.name,
            proficiencyLevel = userSkill.proficiencyLevel,
            hourlyCredits = userSkill.hourlyCredits,
            yearsOfExperience = userSkill.yearsOfExperience,
            teachingBio = userSkill.teachingBio,
            proofUrl = userSkill.proofUrl,
            isActive = userSkill.isActive
        )
    }

    /**
     * Runs every 6 hours. Auto-deactivates published TEACHER skills once
     * they're no longer actually bookable, so Explore doesn't keep showing
     * a teacher card with no way to book them.
     *
     * A skill only qualifies once it's been untouched for 3+ days (updatedAt
     * cutoff) — that grace window is what protects a teacher who just
     * published and hasn't added availability yet, or who briefly runs out
     * of slots between batches, from getting silently deactivated. Of those,
     * only the ones whose teacher currently has zero unbooked future
     * availability slots are turned off — availability isn't tracked per
     * skill in this schema, so "bookable" is inherently a teacher-level
     * question, not a per-skill one.
     *
     * This is a soft deactivation (isActive = false), the same mechanism
     * removeUserSkill() already uses — never a hard delete. That preserves
     * the teacher's session history and ratings, and re-adding availability
     * naturally makes the skill reappear once they re-publish or edit it.
     */
    @Transactional
    @Scheduled(cron = "0 0 */6 * * *")
    @CacheEvict(value = ["discovery-teachers"], allEntries = true, cacheManager = "redisCacheManager")
    fun deactivateStaleTeacherSkills() {
        try {
            val now = java.time.LocalDateTime.now()
            val cutoff = now.minusDays(3)
            val candidates = userSkillRepository.findStaleActiveTeacherSkills(cutoff)
            if (candidates.isEmpty()) return

            val bookableTeacherIds = teacherAvailabilityRepository
                .findDistinctTeacherIdsWithAvailableSlots(now)
                .toHashSet()

            var deactivatedCount = 0
            for (skill in candidates) {
                val teacherId = skill.user?.id ?: continue
                if (teacherId !in bookableTeacherIds) {
                    skill.isActive = false
                    userSkillRepository.save(skill)
                    deactivatedCount++
                }
            }
            if (deactivatedCount > 0) {
                logger.info("Auto-deactivated $deactivatedCount stale teacher skill(s) with no bookable availability")
            }
        } catch (e: Exception) {
            logger.error("deactivateStaleTeacherSkills error: ${e.message}", e)
        }
    }
}