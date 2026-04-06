package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.model.ReportStatus
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.ReportService
import com.exchangemingle.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/reports")
class ReportController(
    private val reportService: ReportService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @PostMapping
    fun createReport(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: CreateReportRequest
    ): ResponseEntity<ReportResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        val report = reportService.createReport(user.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(report)
    }

    @GetMapping("/{id}")
    fun getReportById(@PathVariable id: Long): ResponseEntity<ReportResponse> {
        return ResponseEntity.ok(reportService.getReportById(id))
    }

    @GetMapping
    fun getAllReports(
        @RequestParam(required = false) status: ReportStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedReportResponse> {
        return ResponseEntity.ok(reportService.getAllReports(page, size, status))
    }

    @GetMapping("/my-reports")
    fun getMyReports(
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedReportResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(reportService.getMyReports(user.id, page, size))
    }

    @PatchMapping("/{id}/status")
    fun updateReportStatus(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: UpdateReportStatusRequest
    ): ResponseEntity<ReportResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val admin = userService.findByEmail(email)

        // TODO: Add admin role check

        return ResponseEntity.ok(reportService.updateReportStatus(id, admin.id, request))
    }
}