package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.ForgotPasswordRequest
import com.exchangemingle.backend.dto.MessageResponse
import com.exchangemingle.backend.dto.ResetPasswordRequest
import com.exchangemingle.backend.dto.VerifyResetCodeRequest
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
        passwordResetService.createPasswordResetCode(request.email)
        return ResponseEntity.ok(MessageResponse("Verification code sent to your email"))
    }

    @PostMapping("/verify-reset-code")
    fun verifyResetCode(@Valid @RequestBody request: VerifyResetCodeRequest): ResponseEntity<MessageResponse> {
        passwordResetService.verifyResetCode(request.email, request.code)
        return ResponseEntity.ok(MessageResponse("Code verified"))
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<MessageResponse> {
        passwordResetService.resetPassword(request.email, request.code, request.newPassword, request.confirmPassword)
        return ResponseEntity.ok(MessageResponse("Password reset successfully"))
    }
}