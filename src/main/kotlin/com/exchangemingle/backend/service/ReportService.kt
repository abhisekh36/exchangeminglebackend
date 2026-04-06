package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.Report
import com.exchangemingle.backend.model.ReportStatus
import com.exchangemingle.backend.repository.ReportRepository
import com.exchangemingle.backend.repository.SessionRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val pushNotificationService: PushNotificationService
) {

    @Transactional
    fun createReport(reporterId: Long, request: CreateReportRequest): ReportResponse {
        val reporter = userRepository.findById(reporterId)
            .orElseThrow { UserNotFoundException("Reporter not found: $reporterId") }

        // Validate reported user if provided
        val reportedUser = request.reportedUserId?.let {
            userRepository.findById(it)
                .orElseThrow { UserNotFoundException("Reported user not found: $it") }
        }

        // Validate session if provided
        val session = request.sessionId?.let {
            sessionRepository.findById(it).orElse(null)
        }

        val report = Report(
            reporter = reporter,
            reportedUser = reportedUser,
            session = session,
            reportType = request.reportType,
            description = request.description,
            status = ReportStatus.PENDING
        )

        val savedReport = reportRepository.save(report)

        // Check if user has multiple reports - auto-suspend if needed
        reportedUser?.let { user ->
            val reportCount = reportRepository.countResolvedReportsByUser(user)
            if (reportCount >= 3) {
                // Auto-suspend user
                user.isActive = false
                userRepository.save(user)

                // Notify user
                user.fcmToken?.let { token ->
                    pushNotificationService.sendNotification(
                        deviceToken = token,
                        title = "Account Suspended",
                        body = "Your account has been suspended due to multiple reports. Contact support for more information.",
                        data = mapOf("type" to "ACCOUNT_SUSPENDED")
                    )
                }
            }
        }

        return mapToReportResponse(savedReport)
    }

    fun getReportById(reportId: Long): ReportResponse {
        val report = reportRepository.findById(reportId)
            .orElseThrow { throw RuntimeException("Report not found: $reportId") }
        return mapToReportResponse(report)
    }

    fun getAllReports(page: Int = 0, size: Int = 20, status: ReportStatus? = null): PagedReportResponse {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())

        val reportsPage = if (status != null) {
            reportRepository.findByStatus(status, pageable)
        } else {
            reportRepository.findAll(pageable)
        }

        return PagedReportResponse(
            content = reportsPage.content.map { mapToReportResponse(it) },
            page = reportsPage.number,
            size = reportsPage.size,
            totalElements = reportsPage.totalElements,
            totalPages = reportsPage.totalPages,
            isLast = reportsPage.isLast
        )
    }

    fun getMyReports(reporterId: Long, page: Int = 0, size: Int = 20): PagedReportResponse {
        val reporter = userRepository.findById(reporterId)
            .orElseThrow { UserNotFoundException("User not found: $reporterId") }

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val reportsPage = reportRepository.findByReporter(reporter, pageable)

        return PagedReportResponse(
            content = reportsPage.content.map { mapToReportResponse(it) },
            page = reportsPage.number,
            size = reportsPage.size,
            totalElements = reportsPage.totalElements,
            totalPages = reportsPage.totalPages,
            isLast = reportsPage.isLast
        )
    }

    @Transactional
    fun updateReportStatus(
        reportId: Long,
        adminId: Long,
        request: UpdateReportStatusRequest
    ): ReportResponse {
        val report = reportRepository.findById(reportId)
            .orElseThrow { throw RuntimeException("Report not found: $reportId") }

        val admin = userRepository.findById(adminId)
            .orElseThrow { UserNotFoundException("Admin not found: $adminId") }

        report.status = request.status
        report.adminNotes = request.adminNotes

        if (request.status == ReportStatus.RESOLVED || request.status == ReportStatus.DISMISSED) {
            report.resolvedAt = LocalDateTime.now()
            report.resolvedBy = admin
        }

        val updatedReport = reportRepository.save(report)

        // Notify reporter
        report.reporter?.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                deviceToken = token,
                title = "Report Updated",
                body = "Your report has been ${request.status.name.lowercase().replace('_', ' ')}",
                data = mapOf(
                    "type" to "REPORT_UPDATE",
                    "reportId" to reportId.toString(),
                    "status" to request.status.name
                )
            )
        }

        return mapToReportResponse(updatedReport)
    }

    private fun mapToReportResponse(report: Report): ReportResponse {
        return ReportResponse(
            id = report.id,
            reporter = UserResponse(
                id = report.reporter!!.id,
                email = report.reporter!!.email,
                name = report.reporter!!.name,
                credits = report.reporter!!.credits,
                isEmailVerified = report.reporter!!.isEmailVerified
            ),
            reportedUser = report.reportedUser?.let {
                UserResponse(
                    id = it.id,
                    email = it.email,
                    name = it.name,
                    credits = it.credits,
                    isEmailVerified = it.isEmailVerified
                )
            },
            sessionId = report.session?.id,
            reportType = report.reportType,
            description = report.description,
            status = report.status,
            adminNotes = report.adminNotes,
            createdAt = report.createdAt,
            resolvedAt = report.resolvedAt
        )
    }
}