package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val dashboardService: DashboardService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @GetMapping("/home")
    fun getHomeDashboard(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<DashboardResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(dashboardService.getHomeDashboard(user.id))
    }
}