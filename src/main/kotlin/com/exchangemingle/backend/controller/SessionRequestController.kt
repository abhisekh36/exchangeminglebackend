package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/requests")
class SessionRequestController(
    private val sessionRequestService: SessionRequestService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @PostMapping
    fun createRequest(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody dto: CreateSessionRequestDto
    ): ResponseEntity<SessionRequestResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        val response = sessionRequestService.createRequest(user.id, dto)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/{id}/accept")
    fun acceptRequest(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<SessionRequestResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(sessionRequestService.acceptRequest(id, user.id))
    }

    @DeleteMapping("/{id}")
    fun cancelRequest(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<SessionRequestResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(sessionRequestService.cancelRequest(id, user.id))
    }

    @GetMapping("/mine")
    fun getMyRequests(
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedSessionRequestResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(sessionRequestService.getMyRequests(user.id, page, size))
    }

    /**
     * Teacher marks that they viewed a learner request (increments viewCount).
     * Called when teacher opens the request detail.
     */
    @PostMapping("/{id}/view")
    fun markViewed(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        sessionRequestService.markViewed(id, user.id)
        return ResponseEntity.ok(mapOf("message" to "View recorded"))
    }

    /**
     * Teacher marks interest in a learner request (increments interestCount, notifies learner).
     */
    @PostMapping("/{id}/interest")
    fun expressInterest(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        sessionRequestService.expressInterest(id, user.id)
        return ResponseEntity.ok(mapOf("message" to "Interest recorded"))
    }
}