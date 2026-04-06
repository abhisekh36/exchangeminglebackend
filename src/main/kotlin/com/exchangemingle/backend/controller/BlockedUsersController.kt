package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.BlockUserRequest
import com.exchangemingle.backend.dto.BlockedUserResponse
import com.exchangemingle.backend.service.BlockedUserService
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/blocked-users")
class BlockedUsersController(
    private val blockedUserService: BlockedUserService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @PostMapping
    fun blockUser(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: BlockUserRequest
    ): ResponseEntity<BlockedUserResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(blockedUserService.blockUser(user.id, request))
    }

    @DeleteMapping("/{userId}")
    fun unblockUser(
        @RequestHeader("Authorization") authHeader: String,
        @PathVariable userId: Long
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        blockedUserService.unblockUser(user.id, userId)
        return ResponseEntity.ok(mapOf("message" to "User unblocked successfully"))
    }

    @GetMapping
    fun getBlockedUsers(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<BlockedUserResponse>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(blockedUserService.getBlockedUsers(user.id))
    }
}