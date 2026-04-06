package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.ChangePasswordRequest
import com.exchangemingle.backend.dto.DeleteAccountRequest
import com.exchangemingle.backend.service.AccountManagementService
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/account")
class AccountManagementController(
    private val accountManagementService: AccountManagementService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @PostMapping("/change-password")
    fun changePassword(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        accountManagementService.changePassword(user.id, request)
        return ResponseEntity.ok(mapOf("message" to "Password changed successfully"))
    }

    @PostMapping("/delete")
    fun deleteAccount(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: DeleteAccountRequest
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        accountManagementService.deleteAccount(user.id, request)
        return ResponseEntity.ok(mapOf("message" to "Account deleted successfully"))
    }

    @PostMapping("/request-verification")
    fun requestVerification(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        accountManagementService.requestEmailVerification(user.id)
        return ResponseEntity.ok(mapOf("message" to "Verification email sent"))
    }
}