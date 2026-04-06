package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.GenerateTokenRequest
import com.exchangemingle.backend.dto.StartVideoCallRequest
import com.exchangemingle.backend.dto.VideoCallResponse
import com.exchangemingle.backend.dto.VideoCallTokenResponse
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.UserService
import com.exchangemingle.backend.service.VideoCallService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/video-call")
class VideoCallController(
    private val videoCallService: VideoCallService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @PostMapping("/generate-token")
    fun generateToken(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: GenerateTokenRequest
    ): ResponseEntity<VideoCallTokenResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(
            videoCallService.generateToken(request.sessionId, user.id)
        )
    }

    @PostMapping("/start")
    fun startVideoCall(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: StartVideoCallRequest
    ): ResponseEntity<VideoCallResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        return ResponseEntity.ok(
            videoCallService.startVideoCall(request.sessionId, user.id)
        )
    }
}