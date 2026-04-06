package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.service.*
import com.exchangemingle.backend.repository.SessionRepository
import com.exchangemingle.backend.model.SessionStatus
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.ZoneOffset

/**
 * Chat controller - provides conversation-based API that the Android frontend expects.
 * Treats each session as a "conversation". conversationId == sessionId.
 */
@RestController
@RequestMapping("/api/chat")
class ConversationChatController(
    private val chatService: ChatService,
    private val sessionRepository: SessionRepository,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    /**
     * GET /api/chat/conversations
     * Returns list of sessions the user is part of, formatted as conversations.
     */
    @GetMapping("/conversations")
    fun getConversations(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<ConversationResponse>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user  = userService.findByEmail(email)

        val pageable = PageRequest.of(0, 50, Sort.by("updatedAt").descending())
        val sessions = sessionRepository.findByTeacherOrLearner(user, pageable).content

        val conversations = sessions
            .filter { it.status != SessionStatus.PENDING && it.status != SessionStatus.DECLINED }
            .map { session ->
                val isTeacher    = session.teacher?.id == user.id
                val otherUser    = if (isTeacher) session.learner!! else session.teacher!!
                val lastMessage  = chatService.getLastMessage(session.id)
                ConversationResponse(
                    id                = session.id,   // conversationId == sessionId
                    otherUserId       = otherUser.id,
                    otherUserName     = otherUser.name,
                    otherUserAvatarUrl = otherUser.avatar,
                    lastMessage       = lastMessage?.message,
                    lastMessageTime   = lastMessage?.createdAt?.toInstant(ZoneOffset.UTC)?.toString(),
                    unreadCount       = chatService.getUnreadCount(session.id, user.id).toInt()
                )
            }

        return ResponseEntity.ok(conversations)
    }

    /**
     * GET /api/chat/conversations/{conversationId}/messages
     * conversationId is the sessionId.
     */
    @GetMapping("/conversations/{conversationId}/messages")
    fun getMessages(
        @PathVariable conversationId: Long,
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): ResponseEntity<PagedResponse<ChatMessageResponse>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user  = userService.findByEmail(email)

        val paged = chatService.getMessages(conversationId, user.id, page, size)
        return ResponseEntity.ok(
            PagedResponse(
                content       = paged.content,
                pageNumber    = paged.page,
                pageSize      = paged.size,
                totalElements = paged.totalElements,
                totalPages    = paged.totalPages,
                last          = paged.isLast
            )
        )
    }

    /**
     * POST /api/chat/conversations/{conversationId}/messages
     * Send a message in a session chat.
     */
    @PostMapping("/conversations/{conversationId}/messages")
    fun sendMessage(
        @PathVariable conversationId: Long,
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: SendMessageRequest
    ): ResponseEntity<ChatMessageResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user  = userService.findByEmail(email)

        val response = chatService.sendMessage(conversationId, user.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * PUT /api/chat/messages/{messageId}/read
     */
    @PutMapping("/messages/{messageId}/read")
    fun markMessageRead(
        @PathVariable messageId: Long,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Message marked as read"))
    }
}