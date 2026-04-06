package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.SkillService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/skills")
class SkillController(
    private val skillService: SkillService
) {

    @PostMapping
    fun createSkill(@Valid @RequestBody request: CreateSkillRequest): ResponseEntity<SkillResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.createSkill(request))
    }

    // Called from the app when a teacher types a custom skill not in the list
    @PostMapping("/suggest")
    fun suggestSkill(@Valid @RequestBody request: SuggestSkillRequest): ResponseEntity<SkillResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.suggestSkill(request))
    }

    @GetMapping("/{id}")
    fun getSkillById(@PathVariable id: Long): ResponseEntity<SkillResponse> {
        return ResponseEntity.ok(skillService.getSkillById(id))
    }

    @GetMapping
    fun getAllSkills(
        @RequestParam(defaultValue = "0")   page:   Int,
        @RequestParam(defaultValue = "200") size:   Int,
        @RequestParam(defaultValue = "name") sortBy: String
    ): ResponseEntity<PagedSkillResponse> {
        return ResponseEntity.ok(skillService.getAllSkills(page, size, sortBy))
    }

    @GetMapping("/category/{category}")
    fun getSkillsByCategory(
        @PathVariable category: String,
        @RequestParam(defaultValue = "0")  page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedSkillResponse> {
        return ResponseEntity.ok(skillService.getSkillsByCategory(category, page, size))
    }

    @GetMapping("/search")
    fun searchSkills(
        @RequestParam query: String,
        @RequestParam(defaultValue = "0")  page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedSkillResponse> {
        return ResponseEntity.ok(skillService.searchSkills(query, page, size))
    }

    @GetMapping("/categories")
    fun getAllCategories(): ResponseEntity<List<String>> {
        return ResponseEntity.ok(skillService.getAllCategories())
    }

    @PutMapping("/{id}")
    fun updateSkill(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateSkillRequest
    ): ResponseEntity<SkillResponse> {
        return ResponseEntity.ok(skillService.updateSkill(id, request))
    }

    @DeleteMapping("/{id}")
    fun deleteSkill(@PathVariable id: Long): ResponseEntity<Map<String, String>> {
        skillService.deleteSkill(id)
        return ResponseEntity.ok(mapOf("message" to "Skill deleted successfully"))
    }

    @GetMapping("/advanced-search")
    fun advancedSearch(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) query:    String?,
        @RequestParam(defaultValue = "0")    page:   Int,
        @RequestParam(defaultValue = "20")   size:   Int,
        @RequestParam(defaultValue = "name") sortBy: String
    ): ResponseEntity<PagedSkillResponse> {
        return ResponseEntity.ok(skillService.advancedSearch(category, query, page, size, sortBy))
    }
}