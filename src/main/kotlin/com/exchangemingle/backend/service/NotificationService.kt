package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.Notification
import com.exchangemingle.backend.model.NotificationType
import com.exchangemingle.backend.repository.NotificationRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.ZoneOffset
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val pushNotificationService: PushNotificationService,
    private val sessionRepository: com.exchangemingle.backend.repository.SessionRepository
) {

    @Transactional
    fun create(
        userId: Long,
        type: NotificationType,
        title: String,
        body: String,
        relatedEntityType: String? = null,
        relatedEntityId: Long? = null,
        sendPush: Boolean = true
    ): Notification {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        val notification = Notification(
            user = user,
            type = type,
            title = title,
            body = body,
            isRead = false,
            relatedEntityType = relatedEntityType,
            relatedEntityId = relatedEntityId
        )

        val saved = notificationRepository.save(notification)

        if (sendPush && user.fcmToken != null) {
            pushNotificationService.sendNotification(
                deviceToken = user.fcmToken!!,
                title = title,
                body = body,
                data = mapOf(
                    "type" to type.name,
                    "notificationId" to saved.id.toString(),
                    "relatedEntityType" to (relatedEntityType ?: ""),
                    "relatedEntityId" to (relatedEntityId?.toString() ?: "")
                )
            )
        }

        return saved
    }

    fun getNotifications(userId: Long, page: Int = 0, size: Int = 20): PagedNotificationResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val paged = notificationRepository.findByUser(user, pageable)

        return PagedNotificationResponse(
            content = paged.content.map { mapToResponse(it) },
            page = paged.number, size = paged.size,
            totalElements = paged.totalElements, totalPages = paged.totalPages,
            isLast = paged.isLast
        )
    }

    @Transactional
    fun markRead(notificationId: Long, userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }
        notificationRepository.markReadById(notificationId, user)
    }

    @Transactional
    fun markAllRead(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }
        notificationRepository.markAllReadByUser(user)
    }

    fun getUnreadCount(userId: Long): Long {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found: $userId") }
        return notificationRepository.countUnreadByUser(user)
    }

    private fun mapToResponse(n: Notification): NotificationResponse {
        return NotificationResponse(
            id                = n.id,
            userId            = n.user!!.id,
            type              = n.type.name,
            title             = n.title,
            message           = n.body,
            isRead            = n.isRead,
            relatedEntityType = n.relatedEntityType,
            relatedEntityId   = n.relatedEntityId,
            createdAt         = n.createdAt.toInstant(ZoneOffset.UTC).toString()
        )
    }
    // ── Raw push — for Postman testing ───────────────────────────────────────
    fun sendRawPush(deviceToken: String, title: String, body: String,
                    data: Map<String, String> = emptyMap()): Boolean =
        pushNotificationService.sendNotification(deviceToken, title, body, data)

    // ── Force-fire the 5-min reminder for a specific session (testing) ───────
    fun sendSessionReminderById(sessionId: Long) {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { RuntimeException("Session $sessionId not found") }
        val data = mapOf(
            "type"      to "SESSION_REMINDER",
            "sessionId" to sessionId.toString(),
            "action"    to "JOIN_SESSION"
        )
        session.teacher?.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                token,
                "⏰ Session starting soon!",
                "Your session with ${session.learner?.name} starts in ~5 minutes!",
                data
            )
        }
        session.learner?.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                token,
                "⏰ Session starting soon!",
                "Your session with ${session.teacher?.name} starts in ~5 minutes!",
                data
            )
        }
    }

}