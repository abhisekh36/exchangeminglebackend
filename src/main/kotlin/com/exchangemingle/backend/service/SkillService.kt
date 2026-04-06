package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.InvalidRequestException
import com.exchangemingle.backend.exception.SkillAlreadyExistsException
import com.exchangemingle.backend.exception.SkillNotFoundException
import com.exchangemingle.backend.model.Skill
import com.exchangemingle.backend.repository.SkillRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SkillService(
    private val skillRepository: SkillRepository
) {


    // Lightweight blocklist — only checked for user-submitted skill names
    private val blockedTerms = setOf(
        "sex","porn","nude","naked","escort","prostitute","drug","cocaine","heroin",
        "weed","marijuana","meth","hack","crack","bomb","weapon","kill","murder",
        "racist","nigger","nigga","faggot","cunt","fuck","shit","bitch","asshole",
        "pussy","dick","cock","whore","slut","rape","pedophile","terrorist","isis",
        "suicide","genocide"
    )

    private fun containsOffensiveTerm(input: String): Boolean {
        val lower = input.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
        return blockedTerms.any { term -> lower.split(" ").any { it == term } }
    }

    @Transactional
    fun suggestSkill(request: SuggestSkillRequest): SkillResponse {
        val name = request.name.trim()

        if (name.length !in 2..100) {
            throw InvalidRequestException("Skill name must be between 2 and 100 characters")
        }
        if (containsOffensiveTerm(name)) {
            throw InvalidRequestException("This skill name contains inappropriate content and cannot be added")
        }

        // If it already exists (exact match), just return it
        val exactMatch = skillRepository.findByName(name).orElse(null)
        if (exactMatch != null) return mapToSkillResponse(exactMatch)

        // If it already exists (case-insensitive match), return it
        if (skillRepository.existsByNameIgnoreCase(name)) {
            val existing = skillRepository.findByNameContainingIgnoreCase(name).firstOrNull()
            if (existing != null) return mapToSkillResponse(existing)
        }

        val category = request.category?.trim()?.takeIf { it.isNotBlank() } ?: "Other"
        val skill = skillRepository.save(
            Skill(name = name, category = category, description = request.description?.trim())
        )
        return mapToSkillResponse(skill)
    }

    // ── Standard CRUD ────────────────────────────────────────────────────

    @Transactional
    fun createSkill(request: CreateSkillRequest): SkillResponse {
        if (skillRepository.existsByNameIgnoreCase(request.name.trim())) {
            throw SkillAlreadyExistsException("Skill with name '${request.name}' already exists")
        }
        val skill = Skill(
            name        = request.name.trim(),
            category    = request.category.trim(),
            description = request.description?.trim()
        )
        return mapToSkillResponse(skillRepository.save(skill))
    }

    fun getSkillById(id: Long): SkillResponse {
        val skill = skillRepository.findById(id)
            .orElseThrow { SkillNotFoundException("Skill not found with id: $id") }
        return mapToSkillResponse(skill)
    }

    fun getAllSkills(page: Int = 0, size: Int = 200, sortBy: String = "name"): PagedSkillResponse {
        val pageable   = PageRequest.of(page, size, Sort.by(sortBy).ascending())
        val skillsPage = skillRepository.findAll(pageable)
        return PagedSkillResponse(
            content       = skillsPage.content.map { mapToSkillResponse(it) },
            page          = skillsPage.number,
            size          = skillsPage.size,
            totalElements = skillsPage.totalElements,
            totalPages    = skillsPage.totalPages,
            isLast        = skillsPage.isLast
        )
    }

    fun getSkillsByCategory(category: String, page: Int = 0, size: Int = 20): PagedSkillResponse {
        val pageable   = PageRequest.of(page, size, Sort.by("name").ascending())
        val skillsPage = skillRepository.findByCategory(category, pageable)
        return PagedSkillResponse(
            content       = skillsPage.content.map { mapToSkillResponse(it) },
            page          = skillsPage.number,
            size          = skillsPage.size,
            totalElements = skillsPage.totalElements,
            totalPages    = skillsPage.totalPages,
            isLast        = skillsPage.isLast
        )
    }

    fun searchSkills(query: String, page: Int = 0, size: Int = 20): PagedSkillResponse {
        val pageable   = PageRequest.of(page, size, Sort.by("name").ascending())
        val skillsPage = skillRepository.searchSkills(query.trim(), pageable)
        return PagedSkillResponse(
            content       = skillsPage.content.map { mapToSkillResponse(it) },
            page          = skillsPage.number,
            size          = skillsPage.size,
            totalElements = skillsPage.totalElements,
            totalPages    = skillsPage.totalPages,
            isLast        = skillsPage.isLast
        )
    }

    fun getAllCategories(): List<String> = skillRepository.findAllCategories()

    @Transactional
    fun updateSkill(id: Long, request: UpdateSkillRequest): SkillResponse {
        val skill = skillRepository.findById(id)
            .orElseThrow { SkillNotFoundException("Skill not found with id: $id") }
        request.name?.let {
            val trimmedName = it.trim()
            if (trimmedName.lowercase() != skill.name.lowercase() &&
                skillRepository.existsByNameIgnoreCase(trimmedName)) {
                throw SkillAlreadyExistsException("Skill with name '$trimmedName' already exists")
            }
            skill.name = trimmedName
        }
        request.category?.let { skill.category = it.trim() }
        request.description?.let { skill.description = it.trim() }
        return mapToSkillResponse(skillRepository.save(skill))
    }

    @Transactional
    fun deleteSkill(id: Long) {
        if (!skillRepository.existsById(id)) throw SkillNotFoundException("Skill not found with id: $id")
        skillRepository.deleteById(id)
    }

    fun advancedSearch(
        category: String?, query: String?,
        page: Int = 0, size: Int = 20, sortBy: String = "name"
    ): PagedSkillResponse {
        val pageable   = PageRequest.of(page, size, Sort.by(sortBy).ascending())
        val skillsPage = skillRepository.findByFilters(category, query, pageable)
        return PagedSkillResponse(
            content       = skillsPage.content.map { mapToSkillResponse(it) },
            page          = skillsPage.number,
            size          = skillsPage.size,
            totalElements = skillsPage.totalElements,
            totalPages    = skillsPage.totalPages,
            isLast        = skillsPage.isLast
        )
    }

    private fun mapToSkillResponse(skill: Skill) = SkillResponse(
        id          = skill.id,
        name        = skill.name,
        category    = skill.category,
        description = skill.description
    )
}