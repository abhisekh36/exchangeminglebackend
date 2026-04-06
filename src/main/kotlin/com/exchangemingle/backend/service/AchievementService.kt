package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.AchievementResponse
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.*
import com.exchangemingle.backend.repository.AchievementRepository
import com.exchangemingle.backend.repository.SessionRepository
import com.exchangemingle.backend.repository.UserAchievementRepository
import com.exchangemingle.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.annotation.PostConstruct
import java.time.LocalDateTime

@Service
class AchievementService(
    private val achievementRepository: AchievementRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val notificationService: NotificationService
) {

    private val logger = LoggerFactory.getLogger(AchievementService::class.java)

    @PostConstruct
    fun seedAchievements() {
        val definitions = listOf(
            Triple("FIRST_TEACH",        "First Steps",         AchievementCategory.TEACHING)  to Pair(1,  0.5),
            Triple("DEDICATED_TEACHER",  "Dedicated Teacher",   AchievementCategory.TEACHING)  to Pair(10, 1.0),
            Triple("MASTER_TEACHER",     "Master Teacher",      AchievementCategory.TEACHING)  to Pair(50, 3.0),
            Triple("FIRST_LEARN",        "Curious Mind",        AchievementCategory.LEARNING)  to Pair(1,  0.5),
            Triple("EAGER_LEARNER",      "Eager Learner",       AchievementCategory.LEARNING)  to Pair(10, 1.0),
            Triple("PROFILE_COMPLETE",   "All Set Up",          AchievementCategory.MILESTONE) to Pair(1,  1.0)
        )

        for ((info, reward) in definitions) {
            val (key, name, category) = info
            val (requiredCount, creditReward) = reward

            if (achievementRepository.findByKey(key) == null) {
                achievementRepository.save(Achievement(
                    key = key, name = name,
                    description = getDescription(key),
                    creditReward = creditReward,
                    requiredCount = requiredCount,
                    category = category
                ))
                logger.info("Seeded achievement: $key")
            }
        }
    }

    private fun getDescription(key: String): String = when (key) {
        "FIRST_TEACH"       -> "Teach your first session"
        "DEDICATED_TEACHER" -> "Complete 10 teaching sessions"
        "MASTER_TEACHER"    -> "Complete 50 teaching sessions"
        "FIRST_LEARN"       -> "Complete your first learning session"
        "EAGER_LEARNER"     -> "Complete 10 learning sessions"
        "PROFILE_COMPLETE"  -> "Fill in your bio and avatar"
        else                -> ""
    }

    @Transactional
    fun checkAndUpdate(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        val allAchievements = achievementRepository.findAll()

        for (achievement in allAchievements) {
            var ua = userAchievementRepository.findByUserAndAchievement(user, achievement)
            if (ua == null) {
                ua = UserAchievement(user = user, achievement = achievement, currentCount = 0, isUnlocked = false)
                ua = userAchievementRepository.save(ua)
            }

            if (ua.isUnlocked) continue

            val currentCount = computeProgress(user, achievement.key)
            ua.currentCount = currentCount

            if (currentCount >= achievement.requiredCount) {
                ua.isUnlocked = true
                ua.unlockedAt = LocalDateTime.now()
                userAchievementRepository.save(ua)

                user.credits += achievement.creditReward
                userRepository.save(user)

                notificationService.create(
                    userId = user.id,
                    type = NotificationType.BADGE_UNLOCKED,
                    title = "Achievement Unlocked! 🏆",
                    body = "You unlocked '${achievement.name}' and earned ${achievement.creditReward} credits!",
                    relatedEntityType = "ACHIEVEMENT",
                    relatedEntityId = achievement.id
                )

                logger.info("User ${user.id} unlocked achievement: ${achievement.key}")
            } else {
                userAchievementRepository.save(ua)
            }
        }
    }

    fun getAchievements(userId: Long): List<AchievementResponse> {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        val allAchievements = achievementRepository.findAll()
        val userAchievements = userAchievementRepository.findByUser(user)
        val uaMap = userAchievements.associateBy { it.achievement!!.id }

        return allAchievements.map { a ->
            val ua = uaMap[a.id]
            AchievementResponse(
                id = a.id, key = a.key, name = a.name, description = a.description,
                creditReward = a.creditReward, requiredCount = a.requiredCount,
                category = a.category,
                currentCount = ua?.currentCount ?: 0,
                isUnlocked = ua?.isUnlocked ?: false,
                unlockedAt = ua?.unlockedAt
            )
        }
    }

    private fun computeProgress(user: User, key: String): Int = when (key) {
        "FIRST_TEACH", "DEDICATED_TEACHER", "MASTER_TEACHER" -> {
            sessionRepository.countByTeacherAndStatus(user, SessionStatus.COMPLETED).toInt()
        }
        "FIRST_LEARN", "EAGER_LEARNER" -> {
            sessionRepository.countByLearnerAndStatus(user, SessionStatus.COMPLETED).toInt()
        }
        "PROFILE_COMPLETE" -> {
            if (user.bio != null && user.avatar != null) 1 else 0
        }
        else -> 0
    }
}