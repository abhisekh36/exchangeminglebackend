package com.exchangemingle.backend.service

import com.exchangemingle.backend.exception.EmailAlreadyVerifiedException
import com.exchangemingle.backend.exception.InvalidVerificationCodeException
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.EmailVerificationToken
import com.exchangemingle.backend.model.User
import com.exchangemingle.backend.repository.EmailVerificationTokenRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.random.Random

@Service
class EmailVerificationService(
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService
) {

    companion object {
        private const val EXPIRATION_MINUTES = 15L
    }

    @Transactional
    fun createVerificationToken(user: User): String {
        // Delete any existing tokens for this user
        emailVerificationTokenRepository.deleteByUser(user)

        // Generate 6-digit code
        val code = Random.nextInt(100000, 999999).toString()

        val token = EmailVerificationToken(
            code = code,
            user = user,
            expiryDate = Instant.now().plusSeconds(EXPIRATION_MINUTES * 60)
        )

        emailVerificationTokenRepository.save(token)

        // Send email
        emailService.sendVerificationEmail(user.email, code)

        return code
    }

    @Transactional
    fun verifyEmail(code: String): User {
        val token = emailVerificationTokenRepository.findByCode(code)
            .orElseThrow { InvalidVerificationCodeException() }

        // Check if already used
        if (token.isUsed) {
            throw InvalidVerificationCodeException("Verification code has already been used")
        }

        // Check if expired
        if (token.expiryDate.isBefore(Instant.now())) {
            throw InvalidVerificationCodeException("Verification code has expired")
        }

        // Mark token as used
        token.isUsed = true
        emailVerificationTokenRepository.save(token)

        // Mark user as verified
        val user = token.user!!
        user.isEmailVerified = true
        return userRepository.save(user)
    }

    @Transactional
    fun resendVerificationCode(email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found with email: $email") }

        if (user.isEmailVerified) {
            throw EmailAlreadyVerifiedException()
        }

        createVerificationToken(user)
    }
}