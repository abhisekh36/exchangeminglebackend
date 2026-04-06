package com.exchangemingle.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "chat_messages",
    indexes = [
        Index(name = "idx_chat_session", columnList = "session_id"),
        Index(name = "idx_chat_sender", columnList = "sender_id")
    ]
)
class ChatMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    var session: Session? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    var sender: User? = null,

    @Column(nullable = false, length = 1000)
    var message: String = "",

    @Column(nullable = false)
    var isRead: Boolean = false,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun prePersist() {
        createdAt = LocalDateTime.now()
    }
}