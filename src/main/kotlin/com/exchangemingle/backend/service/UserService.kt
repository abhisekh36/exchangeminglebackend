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
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.cache.annotation.Caching
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.CacheEvict


@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val googleOAuthService: GoogleOAuthService,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val achievementService: AchievementService
) : UserDetailsService {

    // Play Store review account: this address never receives its verification
    // email in practice (it's a Gmail address that itself needs a login code
    // to open), so it's auto-verified on registration to skip that screen
    // entirely for the reviewer. Every other account still goes through the
    // normal email-verification flow untouched. Override via the
    // APP_REVIEWER_EMAIL env var if this account ever changes.
    @Value("\${app.reviewer.email:tripventuresco@gmail.com}")
    private lateinit var reviewerEmail: String

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

        if (user.email == reviewerEmail.trim().lowercase()) {
            user.isEmailVerified = true
            // No top-up/purchase flow exists anywhere in this app, so the
            // normal 5.0 starting balance would eventually strand the
            // reviewer with an InsufficientCreditsException while testing
            // session bookings. Give this account a balance high enough
            // that it never runs out.
            user.credits = 100000.0
        }

        val savedUser = userRepository.save(user)
        return mapToUserResponse(savedUser)
    }

    @Cacheable(value = ["user-profiles"], key = "#email")
    fun findByEmail(email: String): User {
        return userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found: $email") }
    }

    // Self-heals the reviewer account if it was created BEFORE this bypass
    // existed (e.g. during the closed-testing setup) and is therefore still
    // sitting unverified and/or low-on-credits in the database. Cheap to
    // call on every login; it's a no-op once both are already fixed, and
    // does nothing for every other user.
    @Caching(evict = [
        CacheEvict(value = ["users"], key = "#user.id"),
        CacheEvict(value = ["user-profiles"], key = "#user.email")
    ])
    @Transactional
    fun ensureReviewerVerified(user: User): User {
        if (user.email == reviewerEmail.trim().lowercase()) {
            var changed = false
            if (!user.isEmailVerified) {
                user.isEmailVerified = true
                changed = true
            }
            if (user.credits < 1000.0) {
                user.credits = 100000.0
                changed = true
            }
            if (changed) {
                return userRepository.save(user)
            }
        }
        return user
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

        // The "All Set Up" (PROFILE_COMPLETE) achievement — and any other
        // achievement whose progress depends on profile fields — was never
        // actually being recalculated anywhere: the backend only recomputed
        // achievement progress inside a POST /achievements/check endpoint
        // that the app never called. So filling in a bio and avatar updated
        // the columns just fine, but nothing ever re-checked whether that
        // unlocked anything. Checking right here, right after the fields
        // that unlock it are saved, is what actually closes that gap.
        achievementService.checkAndUpdate(userId)

        return mapToUserResponse(updatedUser)
    }

    // updateProfile's `request.avatar?.let { ... }` treats a null avatar as "leave
    // unchanged" (that's what lets name-only or bio-only updates work), so it can
    // never be used to actually clear the column. This is the dedicated path for that.
    @CacheEvict(value = ["users", "user-profiles"], key = "#userId")
    @Transactional
    fun clearAvatar(userId: Long): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }
        user.avatar = null
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