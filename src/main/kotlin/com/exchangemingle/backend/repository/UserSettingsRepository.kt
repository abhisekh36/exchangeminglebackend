package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.User
import com.exchangemingle.backend.model.UserSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserSettingsRepository : JpaRepository<UserSettings, Long> {
    fun findByUser(user: User): Optional<UserSettings>
}