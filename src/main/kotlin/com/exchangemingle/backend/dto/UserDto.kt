package com.exchangemingle.backend.dto

data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val credits: Double,
    val isEmailVerified: Boolean,
    val bio: String? = null,  // ✅ NEW
    val avatar: String? = null  // ✅ NEW
)