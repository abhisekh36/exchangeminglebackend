package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.SessionStatus
import com.exchangemingle.backend.repository.SessionRepository
import com.exchangemingle.backend.repository.UserAchievementRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class DashboardService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val discoveryService: DiscoveryService,
    private val notificationService: NotificationService
) {

    fun getHomeDashboard(userId: Long): DashboardResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        val totalTaught = sessionRepository.countByTeacher(user)
        val totalLearned = sessionRepository.countByLearner(user)
        val completedTaught = sessionRepository.countByTeacherAndStatus(user, SessionStatus.COMPLETED)
        val completedLearned = sessionRepository.countByLearnerAndStatus(user, SessionStatus.COMPLETED)
        val avgRating = sessionRepository.getAverageRatingForTeacher(user)
        val creditsEarned = sessionRepository.getTotalCreditsEarnedByTeacher(user) ?: 0.0
        val creditsSpent = sessionRepository.getTotalCreditsSpentByLearner(user) ?: 0.0
        val unlockedCount = userAchievementRepository.findUnlockedByUser(user).size

        val stats = DashboardStats(
            totalSessionsTaught = totalTaught,
            totalSessionsLearned = totalLearned,
            completedSessionsTaught = completedTaught,
            completedSessionsLearned = completedLearned,
            averageRatingAsTeacher = avgRating,
            totalCreditsEarned = creditsEarned,
            totalCreditsSpent = creditsSpent,
            unlockedAchievements = unlockedCount
        )

        val recommendations = discoveryService.getRecommendations(userId)

        val taughtSkillIds = sessionRepository.findSkillIdsByTeacher(userId)
        val openRequests = if (taughtSkillIds.isNotEmpty()) {
            taughtSkillIds.flatMap { skillId ->
                val result = discoveryService.findOpenRequests(skillId = skillId, page = 0, size = 3)
                result.content
            }.take(5)
        } else {
            emptyList()
        }

        val unreadCount = notificationService.getUnreadCount(userId)

        return DashboardResponse(
            user = UserSummary(user.id, user.name, user.avatar),
            credits = user.credits,
            stats = stats,
            recommendedTeachers = recommendations,
            openRequests = openRequests,
            unreadNotifications = unreadCount
        )
    }
}