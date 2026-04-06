package com.exchangemingle.backend.service

import com.exchangemingle.backend.exception.InvalidResetTokenException
import com.exchangemingle.backend.exception.PasswordMismatchException
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.PasswordResetToken
import com.exchangemingle.backend.repository.PasswordResetTokenRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class PasswordResetService(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService
) {

    companion object {
        private const val EXPIRATION_HOURS = 1L
    }

    @Transactional
    fun createPasswordResetToken(email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found with email: $email") }

        // Delete any existing tokens for this user
        passwordResetTokenRepository.deleteByUser(user)

        // Generate token
        val token = UUID.randomUUID().toString()

        val resetToken = PasswordResetToken(
            token = token,
            user = user,
            expiryDate = Instant.now().plusSeconds(EXPIRATION_HOURS * 3600)
        )

        passwordResetTokenRepository.save(resetToken)

        // Send email
        emailService.sendPasswordResetEmail(user.email, token)
    }

    @Transactional
    fun resetPassword(token: String, newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) {
            throw PasswordMismatchException()
        }

        val resetToken = passwordResetTokenRepository.findByToken(token)
            .orElseThrow { InvalidResetTokenException() }

        // Check if already used
        if (resetToken.isUsed) {
            throw InvalidResetTokenException("Reset token has already been used")
        }

        // Check if expired
        if (resetToken.expiryDate.isBefore(Instant.now())) {
            throw InvalidResetTokenException("Reset token has expired")
        }

        // Mark token as used
        resetToken.isUsed = true
        passwordResetTokenRepository.save(resetToken)

        // Update password
        val user = resetToken.user!!
        user.password = passwordEncoder.encode(newPassword)
        userRepository.save(user)

        // Send confirmation email
        emailService.sendPasswordChangedEmail(user.email, user.name)
    }
}