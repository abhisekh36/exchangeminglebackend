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
    // ── TEST ENDPOINT — use this in Postman to verify FCM works ─────────────
    // POST /api/notifications/test
    // Body: { "title": "Hello", "body": "Test message" }
    // Sends a real push to the authenticated user's registered FCM token.
    // If you receive it on the device, Firebase + token registration is working.
    @PostMapping("/test")
    fun testPushNotification(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody payload: Map<String, String>
    ): ResponseEntity<Map<String, Any>> {
        val email = jwtService.extractUsername(authHeader.substring(7))
        val user  = userService.findByEmail(email)

        val token = user.fcmToken
            ?: return ResponseEntity.badRequest().body(
                mapOf(
                    "success" to false,
                    "error"   to "No FCM token registered for this user. " +
                            "Open the app first so it registers the device token, " +
                            "then call POST /api/notifications/register-token."
                )
            )

        val title = payload["title"] ?: "Test Notification"
        val body  = payload["body"]  ?: "Push notifications are working!"

        val sent = notificationService.sendRawPush(
            deviceToken = token,
            title       = title,
            body        = body,
            data        = mapOf("type" to "TEST", "action" to "NONE")
        )

        return if (sent) {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Push sent to token: ${token.take(20)}…",
                "hint"    to "If you don't receive it: (1) check the app is in background, " +
                        "(2) verify FirebaseMessagingService is declared in AndroidManifest.xml, " +
                        "(3) check Firebase console Logs tab for delivery status."
            ))
        } else {
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "error"   to "FCM rejected the message. Check backend logs for the exact " +
                        "FirebaseMessagingException — common causes: expired/invalid token, " +
                        "wrong project credentials."
            ))
        }
    }

    // ── 5-minute pre-session reminder — trigger manually for testing ─────────
    // POST /api/notifications/test-reminder/{sessionId}
    // Immediately fires the 5-min reminder push for both participants of a session.
    @PostMapping("/test-reminder/{sessionId}")
    fun testSessionReminder(
        @PathVariable sessionId: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Map<String, Any>> {
        val email = jwtService.extractUsername(authHeader.substring(7))
        userService.findByEmail(email) // auth check only
        return try {
            notificationService.sendSessionReminderById(sessionId)
            ResponseEntity.ok(mapOf("success" to true, "message" to "5-min reminder sent for session $sessionId"))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("success" to false, "error" to (e.message ?: "Unknown error")))
        }
    }

}