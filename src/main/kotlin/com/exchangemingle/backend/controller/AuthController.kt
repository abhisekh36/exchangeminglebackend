package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.TokenRefreshException
import com.exchangemingle.backend.service.EmailVerificationService
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.RefreshTokenService
import com.exchangemingle.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val authenticationManager: AuthenticationManager,
    private val emailVerificationService: EmailVerificationService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val userResponse = userService.registerUser(request)
        val user = userService.findByEmail(userResponse.email)

        emailVerificationService.createVerificationToken(user)

        val accessToken = jwtService.generateToken(user.email)
        val refreshToken = refreshTokenService.createRefreshToken(user)

        val response = AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken.token,
            expiresIn = 86400,
            user = userResponse
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        val user = userService.findByEmail(request.email)
        val accessToken = jwtService.generateToken(user.email)
        val refreshToken = refreshTokenService.createRefreshToken(user)

        val response = AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken.token,
            expiresIn = 86400,
            user = userService.getUserById(user.id)
        )

        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<Map<String, Any>> {
        val refreshToken = refreshTokenService.findByToken(request.refreshToken)
            .orElseThrow { TokenRefreshException("Refresh token not found") }

        refreshTokenService.verifyExpiration(refreshToken)

        val accessToken = jwtService.generateToken(refreshToken.user!!.email)

        return ResponseEntity.ok(
            mapOf(
                "accessToken" to accessToken,
                "refreshToken" to refreshToken.token,
                "tokenType" to "Bearer",
                "expiresIn" to 86400
            )
        )
    }

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization") authHeader: String): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        refreshTokenService.deleteByUser(user)

        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }

    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest): ResponseEntity<MessageResponse> {
        emailVerificationService.verifyEmail(request.code)
        return ResponseEntity.ok(MessageResponse("Email verified successfully"))
    }

    @PostMapping("/resend-verification")
    fun resendVerification(@Valid @RequestBody request: ResendVerificationRequest): ResponseEntity<MessageResponse> {
        emailVerificationService.resendVerificationCode(request.email)
        return ResponseEntity.ok(MessageResponse("Verification code sent successfully"))
    }

    // ===== GOOGLE OAUTH ENDPOINTS =====

    @PostMapping("/google/login")
    fun googleLogin(@Valid @RequestBody request: GoogleLoginRequest): ResponseEntity<GoogleLoginResponse> {
        return ResponseEntity.ok(userService.loginWithGoogle(request.idToken))
    }

    @PostMapping("/complete-name-setup")
    fun completeNameSetup(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: CompleteNameSetupRequest
    ): ResponseEntity<UserResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(userService.completeNameSetup(user.id, request.name))
    }
}