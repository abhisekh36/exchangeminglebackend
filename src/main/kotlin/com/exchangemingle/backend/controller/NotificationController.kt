package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.*
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val jwtService: JwtService
) {

    // ===== FCM TOKEN REGISTRATION (from old file) =====
    @PostMapping("/register-token")
    fun registerFcmToken(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<MessageResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        val fcmToken = request["fcmToken"] ?: throw IllegalArgumentException("FCM token is required")
        user.fcmToken = fcmToken
        userRepository.save(user)

        return ResponseEntity.ok(MessageResponse("FCM token registered successfully"))
    }

    // ===== NOTIFICATION MANAGEMENT (from new file) =====
    @GetMapping
    fun getNotifications(
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedNotificationResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(notificationService.getNotifications(user.id, page, size))
    }

    @PostMapping("/{id}/read")
    fun markRead(
        @PathVariable id: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        notificationService.markRead(id, user.id)
        return ResponseEntity.ok(mapOf("message" to "Notification marked as read"))
    }

    @PostMapping("/read-all")
    fun markAllRead(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        notificationService.markAllRead(user.id)
        return ResponseEntity.ok(mapOf("message" to "All notifications marked as read"))
    }

    @GetMapping("/unread-count")
    fun getUnreadCount(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<UnreadCountResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)
        return ResponseEntity.ok(UnreadCountResponse(notificationService.getUnreadCount(user.id)))
    }
}