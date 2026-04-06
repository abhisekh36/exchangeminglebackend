package com.exchangemingle.backend.dto

import jakarta.validation.constraints.*

// ===== USER SKILL DTOs =====

data class AddUserSkillRequest(
    @field:NotNull(message = "Skill ID is required")
    val skillId: Long,

    @field:NotBlank(message = "Role is required")
    @field:Pattern(regexp = "TEACHER|LEARNER", message = "Role must be TEACHER or LEARNER")
    val role: String,

    // Only for TEACHER role
    @field:Min(value = 1, message = "Proficiency level must be between 1 and 5")
    @field:Max(value = 5, message = "Proficiency level must be between 1 and 5")
    val proficiencyLevel: Int? = null,

    @field:Min(value = 0, message = "Hourly credits must be positive")
    val hourlyCredits: Double? = null,

    @field:Min(value = 0, message = "Years of experience cannot be negative")
    val yearsOfExperience: Int? = null,

    @field:Size(max = 1000, message = "Teaching bio cannot exceed 1000 characters")
    val teachingBio: String? = null
)

data class UpdateUserSkillRequest(
    @field:Min(value = 1, message = "Proficiency level must be between 1 and 5")
    @field:Max(value = 5, message = "Proficiency level must be between 1 and 5")
    val proficiencyLevel: Int? = null,

    @field:Min(value = 0, message = "Hourly credits must be positive")
    val hourlyCredits: Double? = null,

    @field:Min(value = 0, message = "Years of experience cannot be negative")
    val yearsOfExperience: Int? = null,

    @field:Size(max = 1000, message = "Teaching bio cannot exceed 1000 characters")
    val teachingBio: String? = null,

    val isActive: Boolean? = null
)

data class UserSkillResponse(
    val id: Long,
    val skill: SkillResponse,
    val role: String,
    val proficiencyLevel: Int?,
    val hourlyCredits: Double?,
    val yearsOfExperience: Int?,
    val teachingBio: String?,
    val isActive: Boolean
)

data class TeacherProfileResponse(
    val id: Long,
    val userId: Long,
    val userName: String,
    val userAvatar: String?,
    val skill: SkillResponse,
    val proficiencyLevel: Int?,
    val hourlyCredits: Double?,
    val yearsOfExperience: Int?,
    val teachingBio: String?,
    val averageRating: Double?,
    val totalSessionsTaught: Long,
    val reliabilityScore: Int
)

data class PagedTeacherProfileResponse(
    val content: List<TeacherProfileResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)

data class UserSkillsSummaryResponse(
    val teachingSkills: List<UserSkillResponse>,
    val learningSkills: List<UserSkillResponse>,
    val totalTeachingSkills: Int,
    val totalLearningSkills: Int
)