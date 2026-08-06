package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface SessionRequestRepository : JpaRepository<SessionRequest, Long> {

    @Query(
        value    = "SELECT sr FROM SessionRequest sr JOIN FETCH sr.learner JOIN FETCH sr.skill LEFT JOIN FETCH sr.acceptedBy WHERE sr.status = :status",
        countQuery = "SELECT COUNT(sr) FROM SessionRequest sr WHERE sr.status = :status"
    )
    fun findByStatus(@Param("status") status: SessionRequestStatus, pageable: Pageable): Page<SessionRequest>

    @Query(
        value    = "SELECT sr FROM SessionRequest sr JOIN FETCH sr.learner JOIN FETCH sr.skill LEFT JOIN FETCH sr.acceptedBy WHERE sr.learner = :learner",
        countQuery = "SELECT COUNT(sr) FROM SessionRequest sr WHERE sr.learner = :learner"
    )
    fun findByLearner(@Param("learner") learner: User, pageable: Pageable): Page<SessionRequest>

    @Query("SELECT sr FROM SessionRequest sr JOIN FETCH sr.learner JOIN FETCH sr.skill WHERE sr.status = 'OPEN' AND sr.skill.id = :skillId")
    fun findOpenBySkillId(@Param("skillId") skillId: Long, pageable: Pageable): Page<SessionRequest>

    @Query(
        value    = "SELECT sr FROM SessionRequest sr JOIN FETCH sr.learner JOIN FETCH sr.skill LEFT JOIN FETCH sr.acceptedBy WHERE sr.status = :status AND sr.skill = :skill",
        countQuery = "SELECT COUNT(sr) FROM SessionRequest sr WHERE sr.status = :status AND sr.skill = :skill"
    )
    fun findByStatusAndSkill(@Param("status") status: SessionRequestStatus, @Param("skill") skill: Skill, pageable: Pageable): Page<SessionRequest>

    @Modifying
    @Query("""
        UPDATE SessionRequest sr 
        SET sr.status = 'EXPIRED', sr.updatedAt = :now 
        WHERE sr.status = 'OPEN' AND sr.createdAt < :cutoff
    """)
    fun expireOldRequests(@Param("cutoff") cutoff: LocalDateTime, @Param("now") now: LocalDateTime): Int
}

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByUser(user: User, pageable: Pageable): Page<Notification>

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.isRead = false")
    fun countUnreadByUser(@Param("user") user: User): Long

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user = :user AND n.isRead = false")
    fun markAllReadByUser(@Param("user") user: User)

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.user = :user")
    fun markReadById(@Param("id") id: Long, @Param("user") user: User)
}

@Repository
interface AchievementRepository : JpaRepository<Achievement, Long> {
    fun findByKey(key: String): Achievement?
}

@Repository
interface UserAchievementRepository : JpaRepository<UserAchievement, Long> {

    fun findByUser(user: User): List<UserAchievement>

    fun findByUserAndAchievement(user: User, achievement: Achievement): UserAchievement?

    @Query("SELECT ua FROM UserAchievement ua WHERE ua.user = :user AND ua.isUnlocked = true")
    fun findUnlockedByUser(@Param("user") user: User): List<UserAchievement>
}