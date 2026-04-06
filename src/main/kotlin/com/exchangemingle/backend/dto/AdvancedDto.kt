package com.exchangemingle.backend.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class VerifyEmailRequest(
    @field:NotBlank(message = "Verification code is required")
    val code: String
)

data class ResendVerificationRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)

data class ForgotPasswordRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "Reset token is required")
    val token: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    val newPassword: String,

    @field:NotBlank(message = "Confirm password is required")
    val confirmPassword: String
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "Current password is required")
    val currentPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    val newPassword: String,

    @field:NotBlank(message = "Confirm password is required")
    val confirmPassword: String
)

data class UpdateProfileRequest(
    @field:Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    val name: String? = null,

    @field:Size(max = 500, message = "Bio cannot exceed 500 characters")
    val bio: String? = null,

    val avatar: String? = null
)

data class UpdateEmailRequest(
    @field:NotBlank(message = "New email is required")
    @field:Email(message = "Invalid email format")
    val newEmail: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class MessageResponse(
    val message: String
)