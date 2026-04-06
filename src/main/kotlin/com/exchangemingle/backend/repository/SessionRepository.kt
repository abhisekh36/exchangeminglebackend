package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.Session
import com.exchangemingle.backend.model.SessionStatus
import com.exchangemingle.backend.model.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface SessionRepository : JpaRepository<Session, Long> {

    fun findByTeacher(teacher: User, pageable: Pageable): Page<Session>

    fun findByLearner(learner: User, pageable: Pageable): Page<Session>

    fun findByStatus(status: SessionStatus, pageable: Pageable): Page<Session>

    @Query("SELECT s FROM Session s WHERE s.teacher = :user OR s.learner = :user")
    fun findByTeacherOrLearner(@Param("user") user: User, pageable: Pageable): Page<Session>

    @Query("""
        SELECT s FROM Session s
        LEFT JOIN FETCH s.teacher t
        LEFT JOIN FETCH s.learner l
        LEFT JOIN FETCH s.skill sk
        WHERE s.teacher = :user OR s.learner = :user
    """)
    fun findByTeacherOrLearnerEager(@Param("user") user: User, pageable: Pageable): Page<Session>

    @Query("SELECT s FROM Session s WHERE (s.teacher = :user OR s.learner = :user) AND s.status = :status")
    fun findByTeacherOrLearnerAndStatus(@Param("user") user: User, @Param("status") status: SessionStatus, pageable: Pageable): Page<Session>

    @Query("""
        SELECT s FROM Session s
        LEFT JOIN FETCH s.teacher t
        LEFT JOIN FETCH s.learner l
        LEFT JOIN FETCH s.skill sk
        WHERE (s.teacher = :user OR s.learner = :user) AND s.status = :status
    """)
    fun findByTeacherOrLearnerAndStatusEager(@Param("user") user: User, @Param("status") status: SessionStatus, pageable: Pageable): Page<Session>

    @Query("""
        SELECT s FROM Session s
        LEFT JOIN FETCH s.teacher t
        LEFT JOIN FETCH s.learner l
        LEFT JOIN FETCH s.skill sk
        WHERE s.id = :id
    """)
    fun findByIdEager(@Param("id") id: Long): Session?

    /** Find sessions for a teacher that overlap with the proposed [start, end) window.
     *  Overlap condition: existing.start < proposedEnd AND existing.start + duration > proposedStart
     *  Since JPQL can't compute start+duration, we check: existing.start < endTime AND existing.start > startTime - maxDuration
     *  Then filter in-memory for the exact duration overlap. But to keep it DB-side, we use a generous window:
     *  any session whose scheduledAt falls between (startTime - 3h) and endTime is a candidate, then check in service.
     *
     *  Actually the cleanest approach: since durationMinutes is on the entity, just fetch all sessions
     *  for the teacher in a wide window and let the service filter precisely.
     */
    @Query("""
        SELECT s FROM Session s
        WHERE s.teacher = :teacher
        AND s.status IN :statuses
        AND s.scheduledAt < :endTime
        AND s.scheduledAt > :windowStart
    """)
    fun findConflictingSessionsForTeacher(
        @Param("teacher") teacher: User,
        @Param("startTime") startTime: java.time.LocalDateTime,
        @Param("endTime") endTime: java.time.LocalDateTime,
        @Param("windowStart") windowStart: java.time.LocalDateTime,
        @Param("statuses") statuses: List<SessionStatus>
    ): List<Session>

    @Query("SELECT COUNT(s) FROM Session s WHERE s.teacher = :user")
    fun countByTeacher(@Param("user") user: User): Long

    @Query("SELECT COUNT(s) FROM Session s WHERE s.learner = :user")
    fun countByLearner(@Param("user") user: User): Long

    @Query("SELECT COUNT(s) FROM Session s WHERE s.teacher = :user AND s.status = :status")
    fun countByTeacherAndStatus(@Param("user") user: User, @Param("status") status: SessionStatus): Long

    @Query("SELECT COUNT(s) FROM Session s WHERE s.learner = :user AND s.status = :status")
    fun countByLearnerAndStatus(@Param("user") user: User, @Param("status") status: SessionStatus): Long

    @Query("SELECT AVG(COALESCE(s.teacherRating, s.rating)) FROM Session s WHERE s.teacher = :user AND (s.teacherRating IS NOT NULL OR s.rating IS NOT NULL)")
    fun getAverageRatingForTeacher(@Param("user") user: User): Double?

    @Query("SELECT SUM(s.creditsUsed) FROM Session s WHERE s.teacher = :user AND s.status = 'COMPLETED'")
    fun getTotalCreditsEarnedByTeacher(@Param("user") user: User): Double?

    @Query("SELECT SUM(s.creditsUsed) FROM Session s WHERE s.learner = :user AND s.status = 'COMPLETED'")
    fun getTotalCreditsSpentByLearner(@Param("user") user: User): Double?

    @Query("SELECT DISTINCT s.teacher.id FROM Session s WHERE s.skill.id = :skillId AND s.status = :status")
    fun findTeacherIdsBySkillAndStatus(@Param("skillId") skillId: Long, @Param("status") status: SessionStatus): List<Long>

    @Query("SELECT DISTINCT s.teacher.id FROM Session s WHERE s.status = :status AND s.teacher IS NOT NULL")
    fun findAllTeacherIds(@Param("status") status: SessionStatus): List<Long>

    @Query("SELECT COUNT(s) FROM Session s WHERE s.teacher = :teacher AND s.status = 'COMPLETED' AND s.completedAt >= :since")
    fun countRecentCompletedByTeacher(@Param("teacher") teacher: User, @Param("since") since: LocalDateTime): Long

    @Query("SELECT DISTINCT s.skill.id FROM Session s WHERE s.learner.id = :learnerId AND s.status = 'COMPLETED'")
    fun findSkillIdsByLearner(@Param("learnerId") learnerId: Long): List<Long>

    @Query("SELECT DISTINCT s.skill.id FROM Session s WHERE s.teacher.id = :teacherId AND s.status = 'COMPLETED'")
    fun findSkillIdsByTeacher(@Param("teacherId") teacherId: Long): List<Long>






    @Query("""
    SELECT s FROM Session s 
    WHERE (s.teacher.id = :userId OR s.learner.id = :userId)
    AND (:status IS NULL OR s.status = :status)
    AND (:skillId IS NULL OR s.skill.id = :skillId)
    AND (:startDate IS NULL OR s.scheduledAt >= :startDate)
    AND (:endDate IS NULL OR s.scheduledAt <= :endDate)
    AND (:hasRating IS NULL OR 
     (:hasRating = true AND s.rating IS NOT NULL) OR 
     (:hasRating = false AND s.rating IS NULL))
    AND (:hasFeedback IS NULL OR 
     (:hasFeedback = true AND s.feedback IS NOT NULL) OR 
     (:hasFeedback = false AND s.feedback IS NULL))
    AND (:minRating IS NULL OR s.rating >= :minRating)
""")
    fun searchUserSessions(
        @Param("userId") userId: Long,
        @Param("status") status: SessionStatus?,
        @Param("skillId") skillId: Long?,
        @Param("startDate") startDate: LocalDateTime?,
        @Param("endDate") endDate: LocalDateTime?,
        @Param("hasRating") hasRating: Boolean?,
        @Param("hasFeedback") hasFeedback: Boolean?,
        @Param("minRating") minRating: Int?,
        pageable: Pageable
    ): Page<Session>

    @Query("""
    SELECT s FROM Session s 
    WHERE s.teacher.id = :userId
    AND (:status IS NULL OR s.status = :status)
    AND (:skillId IS NULL OR s.skill.id = :skillId)
    AND (:startDate IS NULL OR s.scheduledAt >= :startDate)
    AND (:endDate IS NULL OR s.scheduledAt <= :endDate)
""")
    fun searchTeacherSessions(
        @Param("userId") userId: Long,
        @Param("status") status: SessionStatus?,
        @Param("skillId") skillId: Long?,
        @Param("startDate") startDate: LocalDateTime?,
        @Param("endDate") endDate: LocalDateTime?,
        pageable: Pageable
    ): Page<Session>

    @Query("""
    SELECT s FROM Session s 
    WHERE s.learner.id = :userId
    AND (:status IS NULL OR s.status = :status)
    AND (:skillId IS NULL OR s.skill.id = :skillId)
    AND (:startDate IS NULL OR s.scheduledAt >= :startDate)
    AND (:endDate IS NULL OR s.scheduledAt <= :endDate)
""")
    fun searchLearnerSessions(
        @Param("userId") userId: Long,
        @Param("status") status: SessionStatus?,
        @Param("skillId") skillId: Long?,
        @Param("startDate") startDate: LocalDateTime?,
        @Param("endDate") endDate: LocalDateTime?,
        pageable: Pageable
    ): Page<Session>
    @Query("""
        SELECT s FROM Session s
        LEFT JOIN FETCH s.teacher
        LEFT JOIN FETCH s.learner
        LEFT JOIN FETCH s.skill
        WHERE s.status = 'CONFIRMED'
        AND s.scheduledAt BETWEEN :windowStart AND :windowEnd
    """)
    fun findConfirmedSessionsInWindow(
        @Param("windowStart") windowStart: LocalDateTime,
        @Param("windowEnd") windowEnd: LocalDateTime
    ): List<Session>

    // Sessions that should start now (scheduledAt is within 2 minutes, still CONFIRMED, no actual start yet)
    @Query("""
        SELECT s FROM Session s
        LEFT JOIN FETCH s.teacher
        LEFT JOIN FETCH s.learner
        LEFT JOIN FETCH s.skill
        WHERE s.status = 'CONFIRMED'
        AND s.scheduledAt BETWEEN :twoMinutesAgo AND :now
        AND s.actualStartTime IS NULL
    """)
    fun findSessionsDueToStart(
        @Param("twoMinutesAgo") twoMinutesAgo: LocalDateTime,
        @Param("now") now: LocalDateTime
    ): List<Session>

    // Sessions that are IN_PROGRESS and student hasn't joined within the penalty window
    @Query("""
        SELECT s FROM Session s
        LEFT JOIN FETCH s.teacher
        LEFT JOIN FETCH s.learner
        WHERE s.status = 'IN_PROGRESS'
        AND s.actualStartTime <= :penaltyTime
        AND s.studentJoinedAt IS NULL
    """)
    fun findInProgressStudentNoShow(@Param("penaltyTime") penaltyTime: LocalDateTime): List<Session>

    // Sessions that are IN_PROGRESS and teacher hasn't joined within the penalty window
    @Query("""
        SELECT s FROM Session s
        LEFT JOIN FETCH s.teacher
        LEFT JOIN FETCH s.learner
        WHERE s.status = 'IN_PROGRESS'
        AND s.actualStartTime <= :penaltyTime
        AND s.teacherJoinedAt IS NULL
    """)
    fun findInProgressTeacherNoShow(@Param("penaltyTime") penaltyTime: LocalDateTime): List<Session>

    // PENDING sessions older than autoDeclineMinutes - teacher never responded
    @Query("""
        SELECT s FROM Session s
        LEFT JOIN FETCH s.teacher
        LEFT JOIN FETCH s.learner
        WHERE s.status = 'PENDING'
        AND s.createdAt <= :cutoff
    """)
    fun findExpiredPendingSessions(@Param("cutoff") cutoff: LocalDateTime): List<Session>
}