package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserProfileController(
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @PutMapping("/profile")
    fun updateProfile(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<UserResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(userService.updateProfile(user.id, request))
    }

    @PostMapping("/change-password")
    fun changePassword(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<MessageResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        userService.changePassword(user.id, request)
        return ResponseEntity.ok(MessageResponse("Password changed successfully"))
    }

    @PutMapping("/email")
    fun updateEmail(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: UpdateEmailRequest
    ): ResponseEntity<UserResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(userService.updateEmail(user.id, request))
    }
}
