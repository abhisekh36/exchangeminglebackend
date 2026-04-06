package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.SearchService
import com.exchangemingle.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchService: SearchService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @PostMapping("/users")
    fun searchUsers(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: UserSearchRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<UserSearchResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(searchService.searchUsers(user.id, request, page, size))
    }

    @PostMapping("/skills")
    fun searchSkills(
        @Valid @RequestBody request: SkillSearchRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedSkillResponse> {
        return ResponseEntity.ok(searchService.searchSkills(request, page, size))
    }

    @PostMapping("/sessions")
    fun searchSessions(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: SessionSearchRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedSessionResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(searchService.searchSessions(user.id, request, page, size))
    }

    @PostMapping("/global")
    fun globalSearch(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: GlobalSearchRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<GlobalSearchResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(searchService.globalSearch(user.id, request, page, size))
    }
}