package com.exchangemingle.backend.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    data class ErrorResponse(
        val timestamp: LocalDateTime = LocalDateTime.now(),
        val status: Int,
        val error: String,
        val message: String,
        val errors: Map<String, String>? = null
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.allErrors.associate {
            val fieldName = (it as FieldError).field
            val errorMessage = it.defaultMessage ?: "Invalid value"
            fieldName to errorMessage
        }
        return ResponseEntity.badRequest().body(
            ErrorResponse(status = HttpStatus.BAD_REQUEST.value(), error = "Validation Failed", message = "Input validation failed", errors = errors)
        )
    }

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExists(ex: UserAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(status = HttpStatus.CONFLICT.value(), error = "User Already Exists", message = ex.message ?: "User already exists")
        )
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(status = HttpStatus.NOT_FOUND.value(), error = "User Not Found", message = ex.message ?: "User not found")
        )
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(status = HttpStatus.UNAUTHORIZED.value(), error = "Authentication Failed", message = "Invalid email or password")
        )
    }

    @ExceptionHandler(TokenRefreshException::class)
    fun handleTokenRefresh(ex: TokenRefreshException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(status = HttpStatus.FORBIDDEN.value(), error = "Token Refresh Failed", message = ex.message ?: "Invalid refresh token")
        )
    }

    @ExceptionHandler(PasswordMismatchException::class)
    fun handlePasswordMismatch(ex: PasswordMismatchException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(
            ErrorResponse(status = HttpStatus.BAD_REQUEST.value(), error = "Password Mismatch", message = ex.message ?: "Passwords do not match")
        )
    }

    @ExceptionHandler(SkillAlreadyExistsException::class)
    fun handleSkillAlreadyExists(ex: SkillAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(status = HttpStatus.CONFLICT.value(), error = "Skill Already Exists", message = ex.message ?: "Skill already exists")
        )
    }

    @ExceptionHandler(SkillNotFoundException::class)
    fun handleSkillNotFound(ex: SkillNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(status = HttpStatus.NOT_FOUND.value(), error = "Skill Not Found", message = ex.message ?: "Skill not found")
        )
    }

    @ExceptionHandler(SessionNotFoundException::class)
    fun handleSessionNotFound(ex: SessionNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(status = HttpStatus.NOT_FOUND.value(), error = "Session Not Found", message = ex.message ?: "Session not found")
        )
    }

    @ExceptionHandler(InsufficientCreditsException::class)
    fun handleInsufficientCredits(ex: InsufficientCreditsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(
            ErrorResponse(status = HttpStatus.PAYMENT_REQUIRED.value(), error = "Insufficient Credits", message = ex.message ?: "Insufficient credits to book this session")
        )
    }

    @ExceptionHandler(InvalidSessionOperationException::class)
    fun handleInvalidSessionOperation(ex: InvalidSessionOperationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(status = HttpStatus.BAD_REQUEST.value(), error = "Invalid Session Operation", message = ex.message ?: "Invalid operation on session")
        )
    }

    @ExceptionHandler(SelfBookingException::class)
    fun handleSelfBooking(ex: SelfBookingException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(status = HttpStatus.BAD_REQUEST.value(), error = "Self Booking Not Allowed", message = ex.message ?: "You cannot book a session with yourself")
        )
    }

    @ExceptionHandler(InvalidVerificationCodeException::class)
    fun handleInvalidVerificationCode(ex: InvalidVerificationCodeException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(status = HttpStatus.BAD_REQUEST.value(), error = "Invalid Verification Code", message = ex.message ?: "Invalid or expired verification code")
        )
    }

    @ExceptionHandler(EmailAlreadyVerifiedException::class)
    fun handleEmailAlreadyVerified(ex: EmailAlreadyVerifiedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(status = HttpStatus.BAD_REQUEST.value(), error = "Email Already Verified", message = ex.message ?: "Email is already verified")
        )
    }

    @ExceptionHandler(InvalidResetTokenException::class)
    fun handleInvalidResetToken(ex: InvalidResetTokenException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(status = HttpStatus.BAD_REQUEST.value(), error = "Invalid Reset Token", message = ex.message ?: "Invalid or expired reset token")
        )
    }

    @ExceptionHandler(InvalidPasswordException::class)
    fun handleInvalidPassword(ex: InvalidPasswordException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(status = HttpStatus.UNAUTHORIZED.value(), error = "Invalid Password", message = ex.message ?: "Current password is incorrect")
        )
    }

    @ExceptionHandler(SessionRequestNotFoundException::class)
    fun handleSessionRequestNotFound(ex: SessionRequestNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(status = HttpStatus.NOT_FOUND.value(), error = "Session Request Not Found", message = ex.message ?: "Session request not found")
        )
    }

    @ExceptionHandler(AchievementNotFoundException::class)
    fun handleAchievementNotFound(ex: AchievementNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(status = HttpStatus.NOT_FOUND.value(), error = "Achievement Not Found", message = ex.message ?: "Achievement not found")
        )
    }

    @ExceptionHandler(NotificationNotFoundException::class)
    fun handleNotificationNotFound(ex: NotificationNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(status = HttpStatus.NOT_FOUND.value(), error = "Notification Not Found", message = ex.message ?: "Notification not found")
        )
    }

    // ===== ADDED: InvalidRequestException Handler =====
    @ExceptionHandler(InvalidRequestException::class)
    fun handleInvalidRequest(ex: InvalidRequestException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Invalid Request",
                message = ex.message ?: "Invalid request"
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(status = HttpStatus.INTERNAL_SERVER_ERROR.value(), error = "Internal Server Error", message = "An unexpected error occurred")
        )
    }
}