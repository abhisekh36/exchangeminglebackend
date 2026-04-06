package com.exchangemingle.backend.dto

import jakarta.validation.constraints.NotNull

data class GenerateTokenRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: Long,
    val channelName: String? = null
)

data class VideoCallTokenResponse(
    val token: String,
    val channelName: String,
    val wsUrl: String,
    val identity: String,
    val expirationTime: Long
)

data class StartVideoCallRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: Long
)

data class VideoCallResponse(
    val callId: String,
    val channelName: String,
    val token: String,
    val wsUrl: String,
    val sessionId: Long,
    val teacherToken: String,
    val learnerToken: String
)