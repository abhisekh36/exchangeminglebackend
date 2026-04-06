package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.RefreshToken
import com.exchangemingle.backend.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByToken(token: String): Optional<RefreshToken>

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    fun deleteByUser(user: User)
}