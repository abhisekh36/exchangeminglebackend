package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.ForgotPasswordRequest
import com.exchangemingle.backend.dto.MessageResponse
import com.exchangemingle.backend.dto.ResetPasswordRequest
import com.exchangemingle.backend.service.PasswordResetService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class PasswordResetController(
    private val passwordResetService: PasswordResetService
) {

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<MessageResponse> {
        passwordResetService.createPasswordResetToken(request.email)
        return ResponseEntity.ok(MessageResponse("Password reset link sent to your email"))
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<MessageResponse> {
        passwordResetService.resetPassword(request.token, request.newPassword, request.confirmPassword)
        return ResponseEntity.ok(MessageResponse("Password reset successfully"))
    }
}