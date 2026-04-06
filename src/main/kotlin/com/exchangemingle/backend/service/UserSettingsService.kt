package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.*
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.ProfileVisibility
import com.exchangemingle.backend.model.User
import com.exchangemingle.backend.model.UserSettings
import com.exchangemingle.backend.repository.UserRepository
import com.exchangemingle.backend.repository.UserSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserSettingsService(
    private val userSettingsRepository: UserSettingsRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun getOrCreateSettings(userId: Long): UserSettingsResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        val settings = userSettingsRepository.findByUser(user)
            .orElseGet {
                val newSettings = UserSettings(user = user)
                userSettingsRepository.save(newSettings)
            }

        return mapToResponse(settings)
    }

    @Transactional
    fun updateSettings(userId: Long, request: UpdateUserSettingsRequest): UserSettingsResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        val settings = userSettingsRepository.findByUser(user)
            .orElseGet { UserSettings(user = user) }

        request.pushNotificationsEnabled?.let { settings.pushNotificationsEnabled = it }
        request.emailNotificationsEnabled?.let { settings.emailNotificationsEnabled = it }
        request.sessionRemindersEnabled?.let { settings.sessionRemindersEnabled = it }
        request.marketingEmailsEnabled?.let { settings.marketingEmailsEnabled = it }
        request.showOnlineStatus?.let { settings.showOnlineStatus = it }
        request.allowSessionRequests?.let { settings.allowSessionRequests = it }
        request.preferredLanguage?.let { settings.preferredLanguage = it }
        request.timezone?.let { settings.timezone = it }
        request.autoAcceptSessions?.let { settings.autoAcceptSessions = it }

        request.profileVisibility?.let { visibility ->
            settings.profileVisibility = ProfileVisibility.valueOf(visibility.uppercase())
        }

        val savedSettings = userSettingsRepository.save(settings)
        return mapToResponse(savedSettings)
    }

    private fun mapToResponse(settings: UserSettings): UserSettingsResponse {
        return UserSettingsResponse(
            pushNotificationsEnabled = settings.pushNotificationsEnabled,
            emailNotificationsEnabled = settings.emailNotificationsEnabled,
            sessionRemindersEnabled = settings.sessionRemindersEnabled,
            marketingEmailsEnabled = settings.marketingEmailsEnabled,
            profileVisibility = settings.profileVisibility.name,
            showOnlineStatus = settings.showOnlineStatus,
            allowSessionRequests = settings.allowSessionRequests,
            preferredLanguage = settings.preferredLanguage,
            timezone = settings.timezone,
            autoAcceptSessions = settings.autoAcceptSessions
        )
    }
}