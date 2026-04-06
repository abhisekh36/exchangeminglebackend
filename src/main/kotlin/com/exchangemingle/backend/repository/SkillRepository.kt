package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.Skill
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface SkillRepository : JpaRepository<Skill, Long> {

    fun findByName(name: String): Optional<Skill>

    fun findByCategory(category: String, pageable: Pageable): Page<Skill>

    fun existsByNameIgnoreCase(name: String): Boolean

    // ✅ ENHANCED: PostgreSQL Full-Text Search
    @Query(
        """
        SELECT s FROM Skill s 
        WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) 
        OR LOWER(s.category) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(s.description) LIKE LOWER(CONCAT('%', :search, '%'))
        ORDER BY 
            CASE 
                WHEN LOWER(s.name) = LOWER(:search) THEN 1
                WHEN LOWER(s.name) LIKE LOWER(CONCAT(:search, '%')) THEN 2
                WHEN LOWER(s.category) = LOWER(:search) THEN 3
                ELSE 4
            END,
            s.name
        """
    )
    fun searchSkills(@Param("search") search: String, pageable: Pageable): Page<Skill>

    @Query("SELECT DISTINCT s.category FROM Skill s ORDER BY s.category")
    fun findAllCategories(): List<String>

    // ✅ NEW: Advanced filter by multiple criteria
    @Query(
        """
        SELECT s FROM Skill s 
        WHERE (:category IS NULL OR s.category = :category)
        AND (:search IS NULL OR 
             LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR 
             LOWER(s.description) LIKE LOWER(CONCAT('%', :search, '%')))
        """
    )
    fun findByFilters(
        @Param("category") category: String?,
        @Param("search") search: String?,
        pageable: Pageable
    ): Page<Skill>

    @Query("""
        SELECT s FROM Skill s 
        WHERE (:query IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.description) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:category IS NULL OR LOWER(s.category) = LOWER(:category))
    """)
    fun searchSkills(
        @Param("query") query: String?,
        @Param("category") category: String?,
        pageable: Pageable
    ): Page<Skill>

    fun findByNameContainingIgnoreCase(name: String): List<Skill>
}