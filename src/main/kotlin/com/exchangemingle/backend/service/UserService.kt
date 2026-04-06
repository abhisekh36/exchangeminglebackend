package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.PasswordMismatchException
import com.exchangemingle.backend.exception.UserAlreadyExistsException
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.exception.InvalidRequestException
import com.exchangemingle.backend.exception.InvalidPasswordException
import com.exchangemingle.backend.model.User
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.CacheEvict


@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val googleOAuthService: GoogleOAuthService,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            .orElseThrow { UsernameNotFoundException("User not found: $username") }

        return org.springframework.security.core.userdetails.User
            .withUsername(user.email)
            .password(user.password)
            .authorities(emptyList())
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(!user.isActive)
            .build()
    }

    @Transactional
    fun registerUser(request: RegisterRequest): UserResponse {
        if (request.password != request.confirmPassword) {
            throw PasswordMismatchException()
        }

        if (userRepository.existsByEmail(request.email)) {
            throw UserAlreadyExistsException("Email already registered: ${request.email}")
        }

        val user = User()
        user.email = request.email.trim().lowercase()
        user.name = request.name.trim()
        user.password = passwordEncoder.encode(request.password)
        user.credits = 5.0
        user.authProvider = "EMAIL"
        user.needsNameSetup = false

        val savedUser = userRepository.save(user)
        return mapToUserResponse(savedUser)
    }

    @Cacheable(value = ["user-profiles"], key = "#email")
    fun findByEmail(email: String): User {
        return userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found: $email") }
    }

    @Cacheable(value = ["users"], key = "#id")
    fun getUserById(id: Long): UserResponse {
        val user = userRepository.findById(id)
            .orElseThrow { UserNotFoundException("User not found: $id") }
        return mapToUserResponse(user)
    }

    private fun mapToUserResponse(user: User): UserResponse {
        return UserResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            credits = user.credits,
            isEmailVerified = user.isEmailVerified,
            bio = user.bio,
            avatar = user.avatar
        )
    }

    @CacheEvict(value = ["users", "user-profiles"], key = "#userId")
    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        request.name?.let { user.name = it.trim() }
        request.bio?.let { user.bio = it.trim() }
        request.avatar?.let { user.avatar = it.trim() }

        val updatedUser = userRepository.save(user)
        return mapToUserResponse(updatedUser)
    }

    @Transactional
    fun changePassword(userId: Long, request: ChangePasswordRequest) {
        if (request.newPassword != request.confirmPassword) {
            throw PasswordMismatchException()
        }

        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        if (!passwordEncoder.matches(request.currentPassword, user.password)) {
            throw InvalidPasswordException()
        }

        user.password = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
    }

    @Transactional
    fun updateEmail(userId: Long, request: UpdateEmailRequest): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw InvalidPasswordException("Password is incorrect")
        }

        if (userRepository.existsByEmail(request.newEmail)) {
            throw UserAlreadyExistsException("Email already registered: ${request.newEmail}")
        }

        user.email = request.newEmail.trim().lowercase()
        user.isEmailVerified = false

        val updatedUser = userRepository.save(user)
        return mapToUserResponse(updatedUser)
    }

    // ===== GOOGLE OAUTH METHODS =====

    @Transactional
    fun loginWithGoogle(idToken: String): GoogleLoginResponse {
        val googleUserInfo = googleOAuthService.verifyIdToken(idToken)
            ?: throw InvalidRequestException("Invalid Google ID token")

        val user = userRepository.findByEmail(googleUserInfo.email)
            .orElseGet { createGoogleUser(googleUserInfo) }

        if (user.authProvider != "GOOGLE") {
            user.authProvider = "GOOGLE"
            user.googleId = googleUserInfo.sub
            user.isEmailVerified = true

            if (user.avatar == null && googleUserInfo.picture != null) {
                user.avatar = googleUserInfo.picture
            }

            userRepository.save(user)
        }

        val accessToken = jwtService.generateToken(user.email)
        val refreshToken = refreshTokenService.createRefreshToken(user).token

        return GoogleLoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = mapToUserResponse(user),
            needsNameSetup = user.needsNameSetup
        )
    }

    @Transactional
    fun completeNameSetup(userId: Long, name: String): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found") }

        if (!user.needsNameSetup) {
            throw InvalidRequestException("Name setup already completed")
        }

        user.name = name
        user.needsNameSetup = false
        val updatedUser = userRepository.save(user)

        return mapToUserResponse(updatedUser)
    }

    private fun createGoogleUser(googleUserInfo: GoogleUserInfo): User {
        val user = User()
        user.email = googleUserInfo.email
        user.name = googleUserInfo.name ?: ""
        user.password = passwordEncoder.encode(generateRandomPassword())
        user.isEmailVerified = true
        user.authProvider = "GOOGLE"
        user.googleId = googleUserInfo.sub
        user.avatar = googleUserInfo.picture
        user.needsNameSetup = googleUserInfo.name.isNullOrBlank()
        user.credits = 5.0

        return userRepository.save(user)
    }

    private fun generateRandomPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return (1..20).map { chars.random() }.joinToString("")
    }
}