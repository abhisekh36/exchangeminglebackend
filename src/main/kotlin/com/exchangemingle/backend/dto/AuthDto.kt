package com.exchangemingle.backend.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 100)
    // Client already filters keystrokes, but requests can come from anywhere
    // (direct API calls, future clients) so the same rule is enforced here:
    // letters, spaces, and apostrophes only — no underscores, hyphens, or digits.
    @field:Pattern(regexp = "^[\\p{L} ']+$", message = "Name can only contain letters, spaces, and apostrophes")
    val name: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, max = 100)
    val password: String,

    @field:NotBlank(message = "Confirm password is required")
    val confirmPassword: String
)

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: UserResponse
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)