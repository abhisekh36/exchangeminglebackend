package com.exchangemingle.backend.dto

import com.exchangemingle.backend.model.SessionRequestStatus
import com.exchangemingle.backend.model.AchievementCategory
import jakarta.validation.constraints.*
import java.time.LocalDateTime

data class CreateSessionRequestDto(
    @field:NotNull(message = "Skill ID is required")
    val skillId: Long,

    @field:NotNull(message = "Duration is required")
    @field:Min(value = 15, message = "Duration must be at least 15 minutes")
    @field:Max(value = 180, message = "Duration cannot exceed 180 minutes")
    val durationMinutes: Int,

    @field:Size(max = 1000, message = "Message cannot exceed 1000 characters")
    val message: String? = null
)

data class SessionRequestResponse(
    val id: Long,
    val learner: UserSummary,
    val skillId: Long,
    val skillName: String,
    val skillCategory: String,
    val durationMinutes: Int,
    val message: String?,
    val status: SessionRequestStatus,
    val acceptedBy: UserSummary?,
    val acceptedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val viewCount: Int = 0,
    val interestCount: Int = 0
)

data class PagedSessionRequestResponse(
    val content: List<SessionRequestResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)

data class UserSummary(
    val id: Long,
    val name: String,
    val avatar: String? = null
)

data class TeacherCard(
    val id: Long,
    val name: String,
    val avatar: String?,
    val averageRating: Double?,
    val totalSessionsTaught: Long,
    val score: Double,
    val bio: String? = null,
    val skillName: String? = null,
    val hourlyCredits: Double? = null,
    val skillId: Long? = null
)

data class PagedTeacherCardResponse(
    val content: List<TeacherCard>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)

data class OpenRequestCard(
    val id: Long,
    val learner: UserSummary,
    val skillName: String,
    val skillCategory: String,
    val durationMinutes: Int,
    val message: String?,
    val createdAt: LocalDateTime,
    val viewCount: Int = 0,
    val interestCount: Int = 0
)

data class PagedOpenRequestResponse(
    val content: List<OpenRequestCard>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)

data class NotificationResponse(
    val id: Long,
    val userId: Long,
    val type: String,       // String so frontend can handle it simply
    val title: String,
    val message: String,    // "body" aliased to "message" for frontend compat
    val isRead: Boolean,
    val relatedEntityType: String?,
    val relatedEntityId: Long?,
    val createdAt: String   // ISO string
)

data class PagedNotificationResponse(
    val content: List<NotificationResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)

data class UnreadCountResponse(
    val unreadCount: Long
)

data class AchievementResponse(
    val id: Long,
    val key: String,
    val name: String,
    val description: String?,
    val creditReward: Double,
    val requiredCount: Int,
    val category: AchievementCategory,
    val currentCount: Int,
    val isUnlocked: Boolean,
    val unlockedAt: LocalDateTime?
)

data class DashboardResponse(
    val user: UserSummary,
    val credits: Double,
    val stats: DashboardStats,
    val recommendedTeachers: List<TeacherCard>,
    val openRequests: List<OpenRequestCard>,
    val unreadNotifications: Long
)

data class DashboardStats(
    val totalSessionsTaught: Long,
    val totalSessionsLearned: Long,
    val completedSessionsTaught: Long,
    val completedSessionsLearned: Long,
    val averageRatingAsTeacher: Double?,
    val totalCreditsEarned: Double,
    val totalCreditsSpent: Double,
    val unlockedAchievements: Int
)

data class SendMessageRequest(
    @field:Size(max = 1000, message = "Message cannot exceed 1000 characters")
    val message: String? = null,   // legacy field

    @field:Size(max = 1000, message = "Content cannot exceed 1000 characters")
    val content: String? = null    // frontend sends "content"
) {
    fun getText(): String = content ?: message ?: ""
}

data class ChatMessageResponse(
    val id: Long,
    val conversationId: Long = 0L,  // sessionId
    val senderId: Long,
    val senderName: String,
    val content: String,            // renamed from "message" to match frontend
    val isRead: Boolean,
    val createdAt: String           // ISO string
)

data class PagedChatResponse(
    val content: List<ChatMessageResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)

// ===== USER SETTINGS DTOs =====
data class UserSettingsResponse(
    val pushNotificationsEnabled: Boolean,
    val emailNotificationsEnabled: Boolean,
    val sessionRemindersEnabled: Boolean,
    val marketingEmailsEnabled: Boolean,
    val profileVisibility: String,
    val showOnlineStatus: Boolean,
    val allowSessionRequests: Boolean,
    val preferredLanguage: String,
    val timezone: String,
    val autoAcceptSessions: Boolean
)

data class UpdateUserSettingsRequest(
    val pushNotificationsEnabled: Boolean?,
    val emailNotificationsEnabled: Boolean?,
    val sessionRemindersEnabled: Boolean?,
    val marketingEmailsEnabled: Boolean?,
    val profileVisibility: String?,
    val showOnlineStatus: Boolean?,
    val allowSessionRequests: Boolean?,
    val preferredLanguage: String?,
    val timezone: String?,
    val autoAcceptSessions: Boolean?
)


// ===== BLOCKED USERS DTOs =====
data class BlockUserRequest(
    @field:NotNull(message = "User ID to block is required")
    val userId: Long,

    val reason: String?
)

data class BlockedUserResponse(
    val id: Long,
    val blockedUser: UserResponse,
    val reason: String?,
    val blockedAt: LocalDateTime
)

// ===== ACCOUNT MANAGEMENT DTOs =====
data class DeleteAccountRequest(
    @field:NotBlank(message = "Password is required")
    val password: String,

    @field:NotBlank(message = "Confirmation is required")
    val confirmation: String // Must be "DELETE MY ACCOUNT"
)

data class ResendVerificationEmailRequest(
    @field:Email(message = "Invalid email format")
    @field:NotBlank(message = "Email is required")
    val email: String
)

// ===== SEARCH DTOs =====
data class UserSearchRequest(
    val query: String? = null,
    val skillIds: List<Long>? = null,
    val minRating: Double? = null,
    val hasAvatar: Boolean? = null,
    val isEmailVerified: Boolean? = null,
    val excludeBlocked: Boolean = true,
    val sortBy: String = "relevance", // relevance, rating, sessions, name
    val sortDirection: String = "desc"
)

data class UserSearchResponse(
    val users: List<UserSearchResult>,
    val totalResults: Int,
    val page: Int,
    val size: Int,
    val totalPages: Int
)

data class UserSearchResult(
    val id: Long,
    val name: String,
    val email: String,
    val bio: String?,
    val avatar: String?,
    val credits: Double,
    val isEmailVerified: Boolean,
    val reliabilityScore: Int,
    val skills: List<SkillResponse>,
    val averageRating: Double?,
    val totalSessionsAsTeacher: Long,
    val totalSessionsAsLearner: Long,
    val isBlocked: Boolean = false
)

data class SessionSearchRequest(
    val status: String? = null,
    val skillId: Long? = null,
    val role: String? = null, // TEACHER, LEARNER, ALL
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    val minRating: Int? = null,
    val hasRating: Boolean? = null,
    val hasFeedback: Boolean? = null,
    val sortBy: String = "scheduledAt", // scheduledAt, createdAt, rating
    val sortDirection: String = "desc"
)

data class SkillSearchRequest(
    val query: String? = null,
    val category: String? = null,
    val sortBy: String = "name", // name, category
    val sortDirection: String = "asc"
)

data class GlobalSearchRequest(
    val query: String,
    val searchType: String = "ALL" // ALL, USERS, SKILLS, SESSIONS
)

data class GlobalSearchResponse(
    val users: List<UserSearchResult>,
    val skills: List<SkillResponse>,
    val sessions: List<SessionResponse>,
    val totalResults: Int
)


data class GoogleLoginRequest(
    @field:NotBlank(message = "ID token is required")
    val idToken: String
)

data class GoogleLoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse,
    val needsNameSetup: Boolean
)

data class CompleteNameSetupRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    val name: String
)

data class GoogleUserInfo(
    val sub: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
    val picture: String?,
    val givenName: String?,
    val familyName: String?
)
// ===== TEACHER AVAILABILITY DTOs =====

data class CreateAvailabilityRequest(
    val slotStart: java.time.LocalDateTime,
    val slotEnd: java.time.LocalDateTime,
    val note: String? = null
)

data class AvailabilitySlotResponse(
    val id: Long,
    val slotStart: String,    // ISO string
    val slotEnd: String,      // ISO string
    val isBooked: Boolean,
    val note: String?
)

/**
 * One open slot window for a specific date.
 * Learner's booking UI uses this to gray out unavailable dates/times.
 */
data class AvailabilityWindowDto(
    val slotId: Long,
    val date: String,           // "yyyy-MM-dd"
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val note: String?
)

data class TeacherAvailabilitySummaryResponse(
    val availableWindows: List<AvailabilityWindowDto>,
    val availableDates: List<String>                    // distinct "yyyy-MM-dd"
)

// ===== TEACHER PUBLIC PROFILE (full detail) =====

data class TeacherPublicProfileResponse(
    val id: Long,
    val name: String,
    val bio: String?,
    val avatar: String?,
    val reliabilityScore: Int,
    val totalSessionsTaught: Long,
    val completedSessions: Long,
    val averageRating: Double?,
    val credits: Double,
    val teachingSkills: List<TeachingSkillDetail>,
    val availableSlots: List<AvailabilitySlotResponse>
)

data class TeachingSkillDetail(
    val userSkillId: Long,
    val skillId: Long,
    val skillName: String,
    val skillCategory: String,
    val proficiencyLevel: Int?,
    val hourlyCredits: Double?,
    val yearsOfExperience: Int?,
    val teachingBio: String?
)

// ===== LEARNER PUBLIC PROFILE (for teacher viewing request) =====

data class LearnerPublicProfileResponse(
    val id: Long,
    val name: String,
    val bio: String?,
    val avatar: String?,
    val reliabilityScore: Int,
    val noShowCount: Int,
    val totalSessionsCompleted: Long,
    val averageRatingGiven: Double?,   // average rating they gave to teachers
    val credits: Double,
    val learningSkills: List<SkillResponse>  // what they want to learn
)

// ===== OPEN REQUEST WITH COUNTS =====

data class OpenRequestCardDetailed(
    val id: Long,
    val learner: UserSummary,
    val skillName: String,
    val skillCategory: String,
    val skillId: Long,
    val durationMinutes: Int,
    val message: String?,
    val status: String,
    val viewCount: Int,
    val interestCount: Int,
    val createdAt: String  // ISO string
)

data class PagedOpenRequestDetailedResponse(
    val content: List<OpenRequestCardDetailed>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)

// ===== CHAT / CONVERSATION DTOs =====

data class ConversationResponse(
    val id: Long,
    val otherUserId: Long,
    val otherUserName: String,
    val otherUserAvatarUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0
)

data class PagedResponse<T>(
    val content: List<T>,
    val pageNumber: Int,
    val pageSize: Int,
    val totalElements: Long,
    val totalPages: Int,
    val last: Boolean
)