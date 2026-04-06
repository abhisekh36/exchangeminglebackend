package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.SessionStatus
import com.exchangemingle.backend.model.SkillRole
import com.exchangemingle.backend.repository.SessionRepository
import com.exchangemingle.backend.repository.TeacherAvailabilityRepository
import com.exchangemingle.backend.repository.UserRepository
import com.exchangemingle.backend.repository.UserSkillRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Provides rich public profile data for both teachers and learners.
 * Used by the frontend to show full detail screens before booking.
 */
@Service
class UserProfileService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val userSkillRepository: UserSkillRepository,
    private val availabilityRepository: TeacherAvailabilityRepository
) {

    /**
     * Full public teacher profile — all teaching skills, stats, availability.
     */
    fun getTeacherPublicProfile(teacherId: Long): TeacherPublicProfileResponse {
        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found: $teacherId") }

        val totalTaught = sessionRepository.countByTeacher(teacher)
        val completed   = sessionRepository.countByTeacherAndStatus(teacher, SessionStatus.COMPLETED)
        val avgRating   = sessionRepository.getAverageRatingForTeacher(teacher)

        // All active teaching skills with full detail
        val teachingSkills = userSkillRepository.findByUserAndRoleAndIsActive(teacher, SkillRole.TEACHER, true)
            .map { us ->
                TeachingSkillDetail(
                    userSkillId       = us.id,
                    skillId           = us.skill!!.id,
                    skillName         = us.skill!!.name,
                    skillCategory     = us.skill!!.category,
                    proficiencyLevel  = us.proficiencyLevel,
                    hourlyCredits     = us.hourlyCredits,
                    yearsOfExperience = us.yearsOfExperience,
                    teachingBio       = us.teachingBio
                )
            }

        // Upcoming unbooked availability slots
        val availableSlots = availabilityRepository
            .findAvailableByTeacher(teacher, LocalDateTime.now())
            .map { slot ->
                AvailabilitySlotResponse(
                    id        = slot.id,
                    slotStart = slot.slotStart.toInstant(ZoneOffset.UTC).toString(),
                    slotEnd   = slot.slotEnd.toInstant(ZoneOffset.UTC).toString(),
                    isBooked  = slot.isBooked,
                    note      = slot.note
                )
            }

        return TeacherPublicProfileResponse(
            id                  = teacher.id,
            name                = teacher.name,
            bio                 = teacher.bio,
            avatar              = teacher.avatar,
            reliabilityScore    = teacher.reliabilityScore,
            totalSessionsTaught = totalTaught,
            completedSessions   = completed,
            averageRating       = avgRating,
            credits             = teacher.credits,
            teachingSkills      = teachingSkills,
            availableSlots      = availableSlots
        )
    }

    /**
     * Full public learner profile — interests, reliability, session history summary.
     * Shown to teachers before they accept/express interest in a request.
     */
    fun getLearnerPublicProfile(learnerId: Long): LearnerPublicProfileResponse {
        val learner = userRepository.findById(learnerId)
            .orElseThrow { UserNotFoundException("Learner not found: $learnerId") }

        val totalCompleted  = sessionRepository.countByLearnerAndStatus(learner, SessionStatus.COMPLETED)

        // Skills the learner wants to learn
        val learningSkills = userSkillRepository.findByUserAndRoleAndIsActive(learner, SkillRole.LEARNER, true)
            .mapNotNull { us ->
                us.skill?.let { s ->
                    SkillResponse(
                        id          = s.id,
                        name        = s.name,
                        category    = s.category,
                        description = s.description
                    )
                }
            }

        return LearnerPublicProfileResponse(
            id                    = learner.id,
            name                  = learner.name,
            bio                   = learner.bio,
            avatar                = learner.avatar,
            reliabilityScore      = learner.reliabilityScore,
            noShowCount           = learner.noShowCount,
            totalSessionsCompleted = totalCompleted,
            averageRatingGiven    = null,  // future: calculate from studentRating column
            credits               = learner.credits,
            learningSkills        = learningSkills
        )
    }
}