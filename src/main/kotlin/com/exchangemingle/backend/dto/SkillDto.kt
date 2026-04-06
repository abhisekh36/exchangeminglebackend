package com.exchangemingle.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateSkillRequest(
    @field:NotBlank(message = "Skill name is required")
    @field:Size(min = 2, max = 100, message = "Skill name must be between 2 and 100 characters")
    val name: String,

    @field:NotBlank(message = "Category is required")
    @field:Size(min = 2, max = 50, message = "Category must be between 2 and 50 characters")
    val category: String,

    @field:Size(max = 500, message = "Description cannot exceed 500 characters")
    val description: String? = null
)

data class UpdateSkillRequest(
    @field:Size(min = 2, max = 100, message = "Skill name must be between 2 and 100 characters")
    val name: String? = null,

    @field:Size(min = 2, max = 50, message = "Category must be between 2 and 50 characters")
    val category: String? = null,

    @field:Size(max = 500, message = "Description cannot exceed 500 characters")
    val description: String? = null
)

// Used when a teacher types a custom skill that doesn't exist yet
data class SuggestSkillRequest(
    @field:NotBlank(message = "Skill name is required")
    @field:Size(min = 2, max = 100, message = "Skill name must be between 2 and 100 characters")
    val name: String,

    @field:Size(max = 50)
    val category: String? = null,

    @field:Size(max = 300)
    val description: String? = null
)

data class SkillResponse(
    val id: Long,
    val name: String,
    val category: String,
    val description: String?
)

data class PagedSkillResponse(
    val content: List<SkillResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)