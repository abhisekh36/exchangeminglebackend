package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.EmailVerificationToken
import com.exchangemingle.backend.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, Long> {
    fun findByCode(code: String): Optional<EmailVerificationToken>

    @Modifying
    @Query("DELETE FROM EmailVerificationToken evt WHERE evt.user = :user")
    fun deleteByUser(user: User)
}