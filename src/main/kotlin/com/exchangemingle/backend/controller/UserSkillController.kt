package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.UserService
import com.exchangemingle.backend.service.UserSkillService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user-skills")
class UserSkillController(
    private val userSkillService: UserSkillService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @PostMapping
    fun addUserSkill(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: AddUserSkillRequest
    ): ResponseEntity<UserSkillResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        val response = userSkillService.addUserSkill(user.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{userSkillId}")
    fun updateUserSkill(
        @RequestHeader("Authorization") authHeader: String,
        @PathVariable userSkillId: Long,
        @Valid @RequestBody request: UpdateUserSkillRequest
    ): ResponseEntity<UserSkillResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(userSkillService.updateUserSkill(user.id, userSkillId, request))
    }

    @DeleteMapping("/{userSkillId}")
    fun removeUserSkill(
        @RequestHeader("Authorization") authHeader: String,
        @PathVariable userSkillId: Long
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        userSkillService.removeUserSkill(user.id, userSkillId)
        return ResponseEntity.ok(mapOf("message" to "Skill removed successfully"))
    }

    @GetMapping
    fun getUserSkills(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<UserSkillsSummaryResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(userSkillService.getUserSkills(user.id))
    }

    @GetMapping("/teaching")
    fun getTeachingSkills(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<UserSkillResponse>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(userSkillService.getTeachingSkills(user.id))
    }

    @GetMapping("/learning")
    fun getLearningSkills(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<UserSkillResponse>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(userSkillService.getLearningSkills(user.id))
    }

    @GetMapping("/teachers/skill/{skillId}")
    fun findTeachersBySkill(
        @PathVariable skillId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedTeacherProfileResponse> {
        return ResponseEntity.ok(userSkillService.findTeachersBySkill(skillId, page, size))
    }
}