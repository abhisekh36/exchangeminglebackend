package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.PasswordResetToken
import com.exchangemingle.backend.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {
    fun findByToken(token: String): Optional<PasswordResetToken>

    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.user = :user")
    fun deleteByUser(user: User)
}