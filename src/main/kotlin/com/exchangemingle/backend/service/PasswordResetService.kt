package com.exchangemingle.backend.service

import com.exchangemingle.backend.exception.InvalidResetTokenException
import com.exchangemingle.backend.exception.PasswordMismatchException
import com.exchangemingle.backend.exception.SamePasswordException
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.PasswordResetToken
import com.exchangemingle.backend.repository.PasswordResetTokenRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.random.Random

/**
 * Password reset via a 6-digit email code (Email -> Code -> New Password),
 * matching the Android app's ForgotPasswordScreen flow exactly.
 *
 * This used to generate a 36-char UUID emailed as a "token" — completely
 * incompatible with the app's 6-digit numeric code input, and the app's
 * /verify-reset-code call had no matching endpoint at all, so the whole
 * flow was broken end to end. The [PasswordResetToken] entity/table are
 * reused as-is (its `token` column just now holds a 6-digit code instead
 * of a UUID — no schema change needed), mirroring the existing, working
 * EmailVerificationService signup-code pattern.
 */
@Service
class PasswordResetService(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService
) {

    companion object {
        private const val EXPIRATION_MINUTES = 15L

        // Fixed reset code for the Play Store reviewer account only. Put
        // this exact code in the Play Console "App access" instructions so
        // the reviewer can complete forgot-password without ever opening
        // the reviewer Gmail inbox (which sits behind its own Google
        // sign-in security challenge that only the developer's phone can
        // clear). Every other account still gets a real random code.
        private const val REVIEWER_RESET_CODE = "482910"
    }

    // Override via the APP_REVIEWER_EMAIL env var if this account ever changes.
    @Value("\${app.reviewer.email:tripventuresco@gmail.com}")
    private lateinit var reviewerEmail: String

    @Transactional
    fun createPasswordResetCode(email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found with email: $email") }

        // Delete any existing codes for this user
        passwordResetTokenRepository.deleteByUser(user)

        val isReviewer = user.email == reviewerEmail.trim().lowercase()

        // Fixed code for the reviewer, real random code for everyone else
        val code = if (isReviewer) REVIEWER_RESET_CODE else Random.nextInt(100000, 999999).toString()

        val resetToken = PasswordResetToken(
            token = code,
            user = user,
            expiryDate = Instant.now().plusSeconds(EXPIRATION_MINUTES * 60)
        )

        passwordResetTokenRepository.save(resetToken)

        // Send email
        emailService.sendPasswordResetEmail(user.email, code)
    }

    /**
     * Validates a code WITHOUT consuming it — lets the app move the user to
     * the "set new password" step before the code is actually spent. The
     * final resetPassword() call re-validates and consumes it for real.
     */
    @Transactional(readOnly = true)
    fun verifyResetCode(email: String, code: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found with email: $email") }

        val resetToken = passwordResetTokenRepository.findByToken(code)
            .orElseThrow { InvalidResetTokenException("Invalid or expired code") }

        if (resetToken.user?.id != user.id) {
            throw InvalidResetTokenException("Invalid or expired code")
        }
        if (resetToken.isUsed) {
            throw InvalidResetTokenException("This code has already been used")
        }
        if (resetToken.expiryDate.isBefore(Instant.now())) {
            throw InvalidResetTokenException("This code has expired")
        }
    }

    @Transactional
    fun resetPassword(email: String, code: String, newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) {
            throw PasswordMismatchException()
        }

        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found with email: $email") }

        val resetToken = passwordResetTokenRepository.findByToken(code)
            .orElseThrow { InvalidResetTokenException("Invalid or expired code") }

        if (resetToken.user?.id != user.id) {
            throw InvalidResetTokenException("Invalid or expired code")
        }
        if (resetToken.isUsed) {
            throw InvalidResetTokenException("This code has already been used")
        }
        if (resetToken.expiryDate.isBefore(Instant.now())) {
            throw InvalidResetTokenException("This code has expired")
        }

        // Reject re-using the same password. Must run before the code is
        // marked used so the user still has a valid, unspent code and can
        // simply retry with a different password on the same screen.
        if (passwordEncoder.matches(newPassword, user.password)) {
            throw SamePasswordException()
        }

        // Mark code as used
        resetToken.isUsed = true
        passwordResetTokenRepository.save(resetToken)

        // Update password
        user.password = passwordEncoder.encode(newPassword)
        userRepository.save(user)

        // Send confirmation email
        emailService.sendPasswordChangedEmail(user.email, user.name)
    }
}