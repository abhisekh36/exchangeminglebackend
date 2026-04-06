package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.ChangePasswordRequest
import com.exchangemingle.backend.dto.DeleteAccountRequest
import com.exchangemingle.backend.exception.InvalidRequestException
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountManagementService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional
    fun changePassword(userId: Long, request: ChangePasswordRequest) {
        if (request.newPassword != request.confirmPassword) {
            throw InvalidRequestException("New password and confirmation do not match")
        }

        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        if (!passwordEncoder.matches(request.currentPassword, user.password)) {
            throw InvalidRequestException("Current password is incorrect")
        }

        if (request.newPassword == request.currentPassword) {
            throw InvalidRequestException("New password must be different from current password")
        }

        user.password = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
    }

    @Transactional
    fun deleteAccount(userId: Long, request: DeleteAccountRequest) {
        if (request.confirmation != "DELETE MY ACCOUNT") {
            throw InvalidRequestException("Invalid confirmation text")
        }

        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw InvalidRequestException("Password is incorrect")
        }

        // Soft delete - mark as inactive instead of hard delete
        user.isActive = false
        user.email = "deleted_${user.id}_${user.email}"
        user.name = "Deleted User"
        user.fcmToken = null
        userRepository.save(user)
    }

    fun requestEmailVerification(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        if (user.isEmailVerified) {
            throw InvalidRequestException("Email is already verified")
        }

        // TODO: Send verification email
        // This would integrate with your email service
    }
}