package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.Report
import com.exchangemingle.backend.model.ReportStatus
import com.exchangemingle.backend.model.User
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

        // Notify the reported user that a report was filed, but do NOT apply
        // any punishment yet — that's what "the system checks if they
        // actually did something bad" means: nothing happens on the strength
        // of an accusation alone. Punishment is only applied in
        // updateReportStatus, and only once an admin has actually reviewed
        // the report and confirmed it (status -> RESOLVED). See the note
        // there for why this used to be broken.

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

        val previousStatus = report.status
        report.status = request.status
        report.adminNotes = request.adminNotes

        if (request.status == ReportStatus.RESOLVED || request.status == ReportStatus.DISMISSED) {
            report.resolvedAt = LocalDateTime.now()
            report.resolvedBy = admin
        }

        val updatedReport = reportRepository.save(report)

        // This is the actual "check if they did something bad, then punish
        // accordingly" system: nothing happens off the back of a raw
        // accusation (see createReport). It's only when an admin has
        // investigated and confirmed the report — moving it to RESOLVED —
        // that any consequence is applied, and the consequence escalates
        // with how many CONFIRMED (resolved) reports this user now has, not
        // with how many people merely complained. A report moved to
        // DISMISSED (investigated and found not credible) never triggers
        // anything.
        //
        // Previously this check ran inside createReport itself, counting
        // already-resolved reports at the moment a brand-new, still-PENDING
        // report came in — so it fired (if at all) on a later, unrelated
        // report rather than at the moment a report was actually confirmed,
        // and a confirmed report on its own never did anything.
        if (request.status == ReportStatus.RESOLVED && previousStatus != ReportStatus.RESOLVED) {
            report.reportedUser?.let { applyPunishment(it) }
        }

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

    /**
     * Graduated punishment applied once a report against [user] has been
     * confirmed (RESOLVED) by an admin. Escalates with the user's total
     * count of confirmed reports so a first offense gets a warning and a
     * reliability hit rather than jumping straight to suspension, while a
     * repeat offender is suspended outright.
     */
    private fun applyPunishment(user: User) {
        val confirmedReportCount = reportRepository.countResolvedReportsByUser(user)

        when {
            confirmedReportCount >= 3 -> {
                user.isActive = false
                userRepository.save(user)
                user.fcmToken?.let { token ->
                    pushNotificationService.sendNotification(
                        deviceToken = token,
                        title = "Account Suspended",
                        body = "Your account has been suspended after multiple confirmed reports. Contact support for more information.",
                        data = mapOf("type" to "ACCOUNT_SUSPENDED")
                    )
                }
            }
            confirmedReportCount == 2L -> {
                user.reliabilityScore = (user.reliabilityScore - 25).coerceAtLeast(0)
                userRepository.save(user)
                user.fcmToken?.let { token ->
                    pushNotificationService.sendNotification(
                        deviceToken = token,
                        title = "Final Warning",
                        body = "A second report against you has been confirmed. One more confirmed report will suspend your account.",
                        data = mapOf("type" to "REPORT_WARNING", "severity" to "final")
                    )
                }
            }
            else -> {
                user.reliabilityScore = (user.reliabilityScore - 10).coerceAtLeast(0)
                userRepository.save(user)
                user.fcmToken?.let { token ->
                    pushNotificationService.sendNotification(
                        deviceToken = token,
                        title = "Report Confirmed",
                        body = "A report against you was reviewed and confirmed. Please review our community guidelines.",
                        data = mapOf("type" to "REPORT_WARNING", "severity" to "first")
                    )
                }
            }
        }
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