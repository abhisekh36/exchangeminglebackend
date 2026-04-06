package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/achievements")
class AchievementController(
    private val achievementService: AchievementService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @GetMapping
    fun getAchievements(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<AchievementResponse>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(achievementService.getAchievements(user.id))
    }

    @PostMapping("/check")
    fun checkProgress(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<AchievementResponse>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        achievementService.checkAndUpdate(user.id)
        return ResponseEntity.ok(achievementService.getAchievements(user.id))
    }
}