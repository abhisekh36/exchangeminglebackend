package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.Report
import com.exchangemingle.backend.model.ReportStatus
import com.exchangemingle.backend.model.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ReportRepository : JpaRepository<Report, Long> {

    fun findByStatus(status: ReportStatus, pageable: Pageable): Page<Report>

    fun findByReporter(reporter: User, pageable: Pageable): Page<Report>

    fun findByReportedUser(reportedUser: User, pageable: Pageable): Page<Report>

    @Query("SELECT COUNT(r) FROM Report r WHERE r.reportedUser = :user AND r.status = 'RESOLVED'")
    fun countResolvedReportsByUser(@Param("user") user: User): Long
}