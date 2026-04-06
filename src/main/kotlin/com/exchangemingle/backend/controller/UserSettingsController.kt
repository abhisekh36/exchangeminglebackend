package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.UpdateUserSettingsRequest
import com.exchangemingle.backend.dto.UserSettingsResponse
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.UserService
import com.exchangemingle.backend.service.UserSettingsService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user-settings")  // Frontend calls /api/user-settings
class UserSettingsController(
    private val userSettingsService: UserSettingsService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @GetMapping
    fun getSettings(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<UserSettingsResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(userSettingsService.getOrCreateSettings(user.id))
    }

    @PutMapping
    fun updateSettings(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: UpdateUserSettingsRequest
    ): ResponseEntity<UserSettingsResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(userSettingsService.updateSettings(user.id, request))
    }
}