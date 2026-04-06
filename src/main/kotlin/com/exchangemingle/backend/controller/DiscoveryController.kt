package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/discovery")
class DiscoveryController(
    private val discoveryService: DiscoveryService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @GetMapping("/teachers")
    fun getTeachers(
        @RequestParam(required = false) skillId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedTeacherCardResponse> {
        return ResponseEntity.ok(discoveryService.findTeachers(skillId, page, size))
    }

    @GetMapping("/open-requests")
    fun getOpenRequests(
        @RequestParam(required = false) skillId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedOpenRequestResponse> {
        return ResponseEntity.ok(discoveryService.findOpenRequests(skillId, page, size))
    }

    @GetMapping("/recommendations")
    fun getRecommendations(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<TeacherCard>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(discoveryService.getRecommendations(user.id))
    }
}