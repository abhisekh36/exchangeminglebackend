package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.LearnerPublicProfileResponse
import com.exchangemingle.backend.dto.TeacherPublicProfileResponse
import com.exchangemingle.backend.dto.UserResponse
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.UserProfileService
import com.exchangemingle.backend.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val jwtService: JwtService,
    private val userProfileService: UserProfileService
) {

    @GetMapping("/me")
    fun getCurrentUser(@RequestHeader("Authorization") authHeader: String): ResponseEntity<UserResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(userService.getUserById(user.id))
    }

    // NOTE: a GET /{id} endpoint returning the full UserResponse (including email)
    // used to live here. It was never called anywhere in the frontend and let any
    // authenticated user enumerate other users' email addresses by ID. Removed —
    // use /{id}/teacher-profile or /{id}/learner-profile for public-safe lookups,
    // and /me for the caller's own full profile.

    /**
     * Full public teacher profile — includes all teaching skills, availability slots,
     * session stats, and ratings. Used on the TeacherProfileScreen in the app.
     */
    @GetMapping("/{id}/teacher-profile")
    fun getTeacherProfile(@PathVariable id: Long): ResponseEntity<TeacherPublicProfileResponse> {
        return ResponseEntity.ok(userProfileService.getTeacherPublicProfile(id))
    }

    /**
     * Full public learner profile — shown to teachers when they view a learner's open request.
     * Includes skill interests, reliability score, session history summary.
     */
    @GetMapping("/{id}/learner-profile")
    fun getLearnerProfile(@PathVariable id: Long): ResponseEntity<LearnerPublicProfileResponse> {
        return ResponseEntity.ok(userProfileService.getLearnerPublicProfile(id))
    }
}