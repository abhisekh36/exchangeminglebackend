package com.exchangemingle.backend.dto

import com.exchangemingle.backend.model.ReportStatus
import com.exchangemingle.backend.model.ReportType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateReportRequest(
    @field:NotNull(message = "Report type is required")
    val reportType: ReportType,

    val reportedUserId: Long? = null,

    val sessionId: Long? = null,

    @field:NotBlank(message = "Description is required")
    @field:Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters")
    val description: String
)

data class ReportResponse(
    val id: Long,
    val reporter: UserResponse,
    val reportedUser: UserResponse?,
    val sessionId: Long?,
    val reportType: ReportType,
    val description: String,
    val status: ReportStatus,
    val adminNotes: String?,
    val createdAt: LocalDateTime,
    val resolvedAt: LocalDateTime?
)

data class PagedReportResponse(
    val content: List<ReportResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)

data class UpdateReportStatusRequest(
    @field:NotNull(message = "Status is required")
    val status: ReportStatus,

    val adminNotes: String? = null
)