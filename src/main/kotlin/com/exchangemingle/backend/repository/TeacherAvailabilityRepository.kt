package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.TeacherAvailability
import com.exchangemingle.backend.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface TeacherAvailabilityRepository : JpaRepository<TeacherAvailability, Long> {

    /** All upcoming available (not booked) slots for a teacher */
    @Query("""
        SELECT ta FROM TeacherAvailability ta
        WHERE ta.teacher = :teacher
        AND ta.isBooked = false
        AND ta.slotStart > :now
        ORDER BY ta.slotStart ASC
    """)
    fun findAvailableByTeacher(
        @Param("teacher") teacher: User,
        @Param("now") now: LocalDateTime
    ): List<TeacherAvailability>

    /** All slots (past + future) for a teacher */
    @Query("""
        SELECT ta FROM TeacherAvailability ta
        WHERE ta.teacher = :teacher
        ORDER BY ta.slotStart DESC
    """)
    fun findAllByTeacher(@Param("teacher") teacher: User): List<TeacherAvailability>

    /** Find future available slots across all teachers (for discovery) */
    @Query("""
        SELECT ta FROM TeacherAvailability ta
        WHERE ta.isBooked = false
        AND ta.slotStart > :now
        ORDER BY ta.slotStart ASC
    """)
    fun findAllAvailable(@Param("now") now: LocalDateTime): List<TeacherAvailability>
    /** Find slots for a teacher that overlap the given time range (for conflict detection) */
    @Query("""
        SELECT ta FROM TeacherAvailability ta
        WHERE ta.teacher = :teacher
        AND ta.slotStart < :slotEnd
        AND ta.slotEnd > :slotStart
    """)
    fun findOverlappingSlots(
        @Param("teacher") teacher: User,
        @Param("slotStart") slotStart: LocalDateTime,
        @Param("slotEnd") slotEnd: LocalDateTime
    ): List<TeacherAvailability>
}