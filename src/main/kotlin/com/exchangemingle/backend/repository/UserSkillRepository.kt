package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.Skill
import com.exchangemingle.backend.model.SkillRole
import com.exchangemingle.backend.model.User
import com.exchangemingle.backend.model.UserSkill
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserSkillRepository : JpaRepository<UserSkill, Long> {

    // Find user's skills by role
    fun findByUserAndRole(user: User, role: SkillRole): List<UserSkill>

    fun findByUserAndRoleAndIsActive(user: User, role: SkillRole, isActive: Boolean): List<UserSkill>

    // Find specific user skill
    fun findByUserAndSkillAndRole(user: User, skill: Skill, role: SkillRole): Optional<UserSkill>

    // Check if user has a skill with specific role
    fun existsByUserAndSkillAndRole(user: User, skill: Skill, role: SkillRole): Boolean

    // Find all teachers for a skill
    fun findBySkillAndRoleAndIsActive(skill: Skill, role: SkillRole, isActive: Boolean): List<UserSkill>

    // Find teachers by skill ID with pagination
    @Query("""
        SELECT us FROM UserSkill us
        WHERE us.skill.id = :skillId
        AND us.role = 'TEACHER'
        AND us.isActive = true
        AND us.user.isActive = true
        AND us.user.status = 'ACTIVE'
    """)
    fun findTeachersBySkillId(
        @Param("skillId") skillId: Long,
        pageable: Pageable
    ): Page<UserSkill>

    // Find all user skills (both teaching and learning)
    fun findByUser(user: User): List<UserSkill>

    fun findByUserAndIsActive(user: User, isActive: Boolean): List<UserSkill>

    // Check if user teaches a specific skill
    @Query("""
        SELECT CASE WHEN COUNT(us) > 0 THEN true ELSE false END
        FROM UserSkill us
        WHERE us.user = :user
        AND us.skill = :skill
        AND us.role = 'TEACHER'
        AND us.isActive = true
    """)
    fun isUserTeachingSkill(@Param("user") user: User, @Param("skill") skill: Skill): Boolean

    // Check if user is learning a specific skill
    @Query("""
        SELECT CASE WHEN COUNT(us) > 0 THEN true ELSE false END
        FROM UserSkill us
        WHERE us.user = :user
        AND us.skill = :skill
        AND us.role = 'LEARNER'
        AND us.isActive = true
    """)
    fun isUserLearningSkill(@Param("user") user: User, @Param("skill") skill: Skill): Boolean

    // Get user's teaching skills
    @Query("""
        SELECT us FROM UserSkill us
        WHERE us.user.id = :userId
        AND us.role = 'TEACHER'
        AND us.isActive = true
        ORDER BY us.createdAt DESC
    """)
    fun findTeachingSkillsByUserId(@Param("userId") userId: Long): List<UserSkill>

    // Get user's learning skills
    @Query("""
        SELECT us FROM UserSkill us
        WHERE us.user.id = :userId
        AND us.role = 'LEARNER'
        AND us.isActive = true
        ORDER BY us.createdAt DESC
    """)
    fun findLearningSkillsByUserId(@Param("userId") userId: Long): List<UserSkill>

    // Count teachers for a skill
    @Query("""
        SELECT COUNT(us)
        FROM UserSkill us
        WHERE us.skill.id = :skillId
        AND us.role = 'TEACHER'
        AND us.isActive = true
        AND us.user.isActive = true
    """)
    fun countTeachersBySkillId(@Param("skillId") skillId: Long): Long

    fun findByRoleAndIsActive(role: SkillRole, isActive: Boolean): List<UserSkill>
}