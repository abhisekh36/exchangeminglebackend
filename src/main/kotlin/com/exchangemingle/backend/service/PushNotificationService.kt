package com.exchangemingle.backend.service

import com.google.firebase.messaging.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PushNotificationService {

    private val logger = LoggerFactory.getLogger(PushNotificationService::class.java)

    /**
     * Send a DATA-ONLY push notification to a specific device.
     *
     * WHY data-only? When a Firebase message has a "notification" block, Android
     * automatically displays it when the app is in the background — but this means
     * FirebaseMessagingService.onMessageReceived() is NOT called. The app never
     * sees the sessionId / type / action fields, so tapping the notification
     * can't deep-link into the correct screen.
     *
     * By sending only data fields, onMessageReceived() is ALWAYS invoked,
     * the client builds and shows the notification itself, and deep-links work.
     */
    fun sendNotification(
        deviceToken: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        return try {
            val allData = buildMap<String, String> {
                put("title", title)
                put("body", body)
                putAll(data)
            }

            val androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build()

            val message = Message.builder()
                .setToken(deviceToken)
                .setAndroidConfig(androidConfig)
                .putAllData(allData)
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("Successfully sent data notification: $response")
            true

        } catch (e: FirebaseMessagingException) {
            when (e.messagingErrorCode) {
                MessagingErrorCode.INVALID_ARGUMENT -> logger.error("Invalid FCM token: $deviceToken")
                MessagingErrorCode.UNREGISTERED -> logger.error("FCM token unregistered: $deviceToken")
                else -> logger.error("Failed to send notification: ${e.message}", e)
            }
            false
        } catch (e: Exception) {
            logger.error("Unexpected error sending notification", e)
            false
        }
    }

    /**
     * Send multicast notification (data-only, same rationale as above).
     */
    fun sendMulticastNotification(
        deviceTokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): BatchResponse {
        val allData = buildMap<String, String> {
            put("title", title)
            put("body", body)
            putAll(data)
        }

        val androidConfig = AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .build()

        val message = MulticastMessage.builder()
            .addAllTokens(deviceTokens)
            .setAndroidConfig(androidConfig)
            .putAllData(allData)
            .build()

        val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)
        logger.info("Sent ${response.successCount} notifications, ${response.failureCount} failed")
        return response
    }

    // ─── Session lifecycle notifications ────────────────────────────────────

    /** Teacher gets this when a learner sends a new session request. */
    fun sendSessionRequestNotification(
        deviceToken: String,
        sessionId: Long,
        learnerName: String,
        skillName: String
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "New Session Request! 📚",
        body = "$learnerName wants to learn $skillName with you",
        data = mapOf(
            "type"      to "SESSION_REQUEST",
            "sessionId" to sessionId.toString(),
            "action"    to "VIEW_SESSION"
        )
    )

    /** Learner gets this when teacher confirms their booking. */
    fun sendSessionConfirmedNotification(
        deviceToken: String,
        sessionId: Long,
        teacherName: String
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "Session Confirmed! 🎉",
        body = "$teacherName has confirmed your session",
        data = mapOf(
            "type"      to "SESSION_CONFIRMED",
            "sessionId" to sessionId.toString(),
            "action"    to "VIEW_SESSION"
        )
    )

    /** Learner gets this when teacher declines their booking. */
    fun sendSessionDeclinedNotification(
        deviceToken: String,
        sessionId: Long,
        teacherName: String,
        reason: String? = null
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "Session Declined",
        body = "$teacherName declined your session." +
                (if (!reason.isNullOrBlank()) " Reason: $reason." else "") +
                " Credits refunded.",
        data = mapOf(
            "type"      to "SESSION_DECLINED",
            "sessionId" to sessionId.toString(),
            "action"    to "VIEW_SESSION"
        )
    )

    /** Both parties get this when a session is cancelled. */
    fun sendSessionCancelledNotification(
        deviceToken: String,
        sessionId: Long,
        cancellerName: String
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "Session Cancelled",
        body = "$cancellerName cancelled the session. Credits have been refunded.",
        data = mapOf(
            "type"      to "SESSION_CANCELLED",
            "sessionId" to sessionId.toString(),
            "action"    to "VIEW_SESSION"
        )
    )

    /** 5-minute reminder sent to both teacher and learner before session. */
    fun sendSessionReminderNotification(
        deviceToken: String,
        sessionId: Long,
        minutesBefore: Int = 5
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "Session Starting Soon! ⏰",
        body = "Your session starts in $minutesBefore minutes. Get ready!",
        data = mapOf(
            "type"      to "SESSION_REMINDER",
            "sessionId" to sessionId.toString(),
            "action"    to "JOIN_SESSION"
        )
    )

    /** Tells both parties to join the video call right now. */
    fun sendSessionJoinNowNotification(
        deviceToken: String,
        sessionId: Long,
        otherUserName: String
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "📹 Session Starting Now!",
        body = "Join your session with $otherUserName now!",
        data = mapOf(
            "type"      to "SESSION_JOIN_NOW",
            "sessionId" to sessionId.toString(),
            "action"    to "OPEN_VIDEO_CALL"
        )
    )

    /** Learner gets this when a teacher accepts their public open request. */
    fun sendSessionAcceptedNotification(
        deviceToken: String,
        requestId: Long,
        teacherName: String,
        skillName: String
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "Your Request Was Accepted! 🎉",
        body = "$teacherName accepted your request to learn $skillName",
        data = mapOf(
            "type"      to "SESSION_ACCEPTED",
            "requestId" to requestId.toString(),
            "action"    to "VIEW_MY_REQUESTS"
        )
    )

    /** Notifies a teacher when a learner posts a new public learning request. */
    fun sendNewLearnerRequestNotification(
        deviceToken: String,
        requestId: Long,
        learnerName: String,
        skillName: String
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "New Learning Request! 📋",
        body = "$learnerName wants to learn $skillName — you could teach them!",
        data = mapOf(
            "type"      to "NEW_LEARNER_REQUEST",
            "requestId" to requestId.toString(),
            "action"    to "VIEW_REQUESTS_FEED"
        )
    )

    /** Learner gets this when a teacher marks interest in their request. */
    fun sendTeacherInterestedNotification(
        deviceToken: String,
        requestId: Long,
        teacherName: String,
        skillName: String
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "A Teacher is Interested! 👋",
        body = "$teacherName is interested in teaching you $skillName",
        data = mapOf(
            "type"      to "TEACHER_INTERESTED",
            "requestId" to requestId.toString(),
            "action"    to "VIEW_MY_REQUESTS"
        )
    )

    // ─── Chat notifications ──────────────────────────────────────────────────

    /** New chat message notification. */
    fun sendNewMessageNotification(
        deviceToken: String,
        senderName: String,
        messagePreview: String,
        sessionId: Long? = null
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "New message from $senderName",
        body = messagePreview,
        data = buildMap {
            put("type",   "NEW_MESSAGE")
            put("sender", senderName)
            put("action", "OPEN_CHAT")
            sessionId?.let { put("sessionId", it.toString()) }
        }
    )

    // ─── No-show notifications ───────────────────────────────────────────────

    fun sendStudentNoShowNotification(
        deviceToken: String,
        sessionId: Long,
        studentName: String
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "Session Completed",
        body = "$studentName didn't join. Credits have been transferred to you.",
        data = mapOf("type" to "STUDENT_NO_SHOW", "sessionId" to sessionId.toString())
    )

    fun sendTeacherNoShowNotification(
        deviceToken: String,
        sessionId: Long,
        teacherName: String
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "Session Cancelled — Full Refund",
        body = "$teacherName didn't join. Your credits have been fully refunded.",
        data = mapOf("type" to "TEACHER_NO_SHOW", "sessionId" to sessionId.toString())
    )

    // ─── Credit notifications ────────────────────────────────────────────────

    fun sendCreditsReceivedNotification(
        deviceToken: String,
        amount: Double,
        sessionId: Long
    ): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "Credits Received! 💰",
        body = "You earned ${"%.1f".format(amount)} credits from your session",
        data = mapOf(
            "type"      to "CREDITS_RECEIVED",
            "amount"    to amount.toString(),
            "sessionId" to sessionId.toString()
        )
    )

    // ─── Account notifications ───────────────────────────────────────────────

    fun sendAccountSuspendedNotification(deviceToken: String): Boolean = sendNotification(
        deviceToken = deviceToken,
        title = "Account Suspended ⚠️",
        body = "Your account has been suspended. Contact support for more information.",
        data = mapOf("type" to "ACCOUNT_SUSPENDED", "action" to "CONTACT_SUPPORT")
    )

    // ─── Topic subscription ──────────────────────────────────────────────────

    fun subscribeToTopic(deviceToken: String, topic: String): Boolean {
        return try {
            FirebaseMessaging.getInstance().subscribeToTopic(listOf(deviceToken), topic)
            logger.info("Subscribed token to topic: $topic")
            true
        } catch (e: Exception) {
            logger.error("Failed to subscribe to topic: $topic", e)
            false
        }
    }
}