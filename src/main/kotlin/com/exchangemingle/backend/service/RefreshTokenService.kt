package com.exchangemingle.backend.service

import com.exchangemingle.backend.exception.TokenRefreshException
import com.exchangemingle.backend.model.RefreshToken
import com.exchangemingle.backend.model.User
import com.exchangemingle.backend.repository.RefreshTokenRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository
) {

    @Value("\${jwt.refresh-expiration:604800000}")
    private var refreshTokenDurationMs: Long = 604800000

    fun createRefreshToken(user: User): RefreshToken {
        val refreshToken = RefreshToken()
        refreshToken.user = user
        refreshToken.token = UUID.randomUUID().toString()
        refreshToken.expiryDate = Instant.now().plusMillis(refreshTokenDurationMs)

        return refreshTokenRepository.save(refreshToken)
    }

    @Transactional(readOnly = true)
    fun findByToken(token: String): Optional<RefreshToken> {
        return refreshTokenRepository.findByToken(token)
    }
    @Transactional(readOnly = true)
    fun verifyExpiration(token: RefreshToken): RefreshToken {
        if (token.expiryDate.isBefore(Instant.now())) {
            refreshTokenRepository.delete(token)
            throw TokenRefreshException("Refresh token expired. Please login again")
        }
        return token
    }

    @Transactional
    fun deleteByUser(user: User) {
        refreshTokenRepository.deleteByUser(user)
    }
}