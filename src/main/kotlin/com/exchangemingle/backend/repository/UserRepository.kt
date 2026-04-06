package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>

    fun existsByEmail(email: String): Boolean

    // ===== SEARCH METHODS - FIXED JPQL =====

    @Query("""
        SELECT DISTINCT u FROM User u 
        WHERE u.isActive = true 
        AND (:query IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) 
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.bio) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:hasAvatar IS NULL OR 
            (:hasAvatar = true AND u.avatar IS NOT NULL) OR 
            (:hasAvatar = false AND u.avatar IS NULL))
        AND (:isEmailVerified IS NULL OR u.isEmailVerified = :isEmailVerified)
        AND (:minReliability IS NULL OR u.reliabilityScore >= :minReliability)
    """)
    fun searchUsers(
        @Param("query") query: String?,
        @Param("hasAvatar") hasAvatar: Boolean?,
        @Param("isEmailVerified") isEmailVerified: Boolean?,
        @Param("minReliability") minReliability: Int?,
        pageable: Pageable
    ): Page<User>

    @Query("""
        SELECT DISTINCT u FROM User u 
        JOIN Session s ON (s.teacher = u OR s.learner = u)
        JOIN s.skill sk
        WHERE u.isActive = true 
        AND sk.id IN :skillIds
        AND s.status = 'COMPLETED'
        AND (:query IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    fun searchUsersBySkills(
        @Param("query") query: String?,
        @Param("skillIds") skillIds: List<Long>,
        pageable: Pageable
    ): Page<User>
}