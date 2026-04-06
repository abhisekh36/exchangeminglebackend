package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.*
import com.exchangemingle.backend.model.ChatMessage
import com.exchangemingle.backend.repository.ChatMessageRepository
import com.exchangemingle.backend.repository.SessionRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.data.domain.PageRequest
import java.util.Optional
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.ZoneOffset
import org.springframework.transaction.annotation.Transactional

@Service
class ChatService(
    private val chatMessageRepository: ChatMessageRepository,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val pushNotificationService: PushNotificationService
) {

    @Transactional
    fun sendMessage(sessionId: Long, senderId: Long, request: SendMessageRequest): ChatMessageResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found: $sessionId") }

        val sender = userRepository.findById(senderId)
            .orElseThrow { UserNotFoundException("User not found: $senderId") }

        // Verify sender is part of the session
        if (session.teacher?.id != senderId && session.learner?.id != senderId) {
            throw InvalidSessionOperationException("You are not part of this session")
        }

        val chatMessage = ChatMessage(
            session = session,
            sender = sender,
            message = request.getText(),
            isRead = false
        )

        val saved = chatMessageRepository.save(chatMessage)

        // Notify the other person
        val receiverId = if (session.teacher?.id == senderId) session.learner?.id else session.teacher?.id
        val receiver = userRepository.findById(receiverId!!).orElse(null)

        receiver?.fcmToken?.let { token ->
            pushNotificationService.sendNotification(
                deviceToken = token,
                title = "${sender.name} sent a message",
                body = request.getText(),
                data = mapOf(
                    "type" to "CHAT_MESSAGE",
                    "sessionId" to sessionId.toString(),
                    "messageId" to saved.id.toString()
                )
            )
        }

        return mapToResponse(saved)
    }

    fun getMessages(sessionId: Long, userId: Long, page: Int = 0, size: Int = 50): PagedChatResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found: $sessionId") }

        // Verify user is part of the session
        if (session.teacher?.id != userId && session.learner?.id != userId) {
            throw InvalidSessionOperationException("You are not part of this session")
        }

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending())
        val messagesPage = chatMessageRepository.findBySession(session, pageable)

        return PagedChatResponse(
            content = messagesPage.content.map { mapToResponse(it) },
            page = messagesPage.number,
            size = messagesPage.size,
            totalElements = messagesPage.totalElements,
            totalPages = messagesPage.totalPages,
            isLast = messagesPage.isLast
        )
    }

    fun getUnreadCount(sessionId: Long, userId: Long): Long {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found: $sessionId") }

        return chatMessageRepository.countUnreadBySessionAndUser(session, userId)
    }

    fun getLastMessage(sessionId: Long): ChatMessage? {
        val session = sessionRepository.findById(sessionId).orElse(null) ?: return null
        val pageable = PageRequest.of(0, 1, Sort.by("createdAt").descending())
        val page = chatMessageRepository.findBySession(session, pageable)
        return page.content.firstOrNull()
    }

    private fun mapToResponse(cm: ChatMessage): ChatMessageResponse {
        return ChatMessageResponse(
            id             = cm.id,
            conversationId = cm.session?.id ?: 0L,
            senderId       = cm.sender!!.id,
            senderName     = cm.sender!!.name,
            content        = cm.message,
            isRead         = cm.isRead,
            createdAt      = cm.createdAt.toInstant(ZoneOffset.UTC).toString()
        )
    }
}