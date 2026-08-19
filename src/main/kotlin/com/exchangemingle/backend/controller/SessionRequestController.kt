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

    /**
     * All PENDING offers on this request — the learner uses this to see
     * every teacher who's offered so far and choose one.
     */
    @GetMapping("/{id}/offers")
    fun getOffers(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<RequestOfferResponse>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(sessionRequestService.getOffers(id, user.id))
    }

    /** Learner picks one of the offering teachers; every other offer is auto-declined. */
    @PostMapping("/{id}/offers/{offerId}/choose")
    fun chooseOffer(
        @PathVariable id: Long,
        @PathVariable offerId: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<SessionRequestResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(sessionRequestService.chooseOffer(id, user.id, offerId))
    }

    /**
     * Lets a teacher resume exactly where they left off on a specific
     * request — whether they've already offered, been chosen, or nothing at
     * all — instead of the screen always starting from scratch.
     */
    @GetMapping("/{id}/my-offer")
    fun getMyOfferForRequest(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<MyOfferForRequestResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(sessionRequestService.getMyOfferForRequest(id, user.id))
    }

    /**
     * The reminder feed: requests where this teacher was chosen but hasn't
     * scheduled a date/time yet.
     */
    @GetMapping("/my-pending-schedules")
    fun getMyChosenUnscheduled(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<ChosenUnscheduledRequestResponse>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(sessionRequestService.getMyChosenUnscheduled(user.id))
    }

    /**
     * Teacher picks a date/time for a request they've already accepted, which
     * actually creates the bookable Session. Previously "Accept & Schedule
     * Session" only did the accept half — there was no way to get from
     * "accepted" to an actual scheduled session, which is what this fixes.
     */
    @PostMapping("/{id}/schedule")
    fun scheduleAcceptedRequest(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody dto: ScheduleAcceptedRequestDto
    ): ResponseEntity<SessionResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        val scheduledAt = java.time.Instant.parse(dto.scheduledStartTime)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        val response = sessionRequestService.scheduleAcceptedRequest(id, user.id, scheduledAt)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
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