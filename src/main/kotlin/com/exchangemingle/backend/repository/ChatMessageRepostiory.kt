package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.ChatMessage
import com.exchangemingle.backend.model.Session
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {

    fun findBySession(session: Session, pageable: Pageable): Page<ChatMessage>

    @Query("SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.session = :session AND cm.sender.id != :userId AND cm.isRead = false")
    fun countUnreadBySessionAndUser(@Param("session") session: Session, @Param("userId") userId: Long): Long
}