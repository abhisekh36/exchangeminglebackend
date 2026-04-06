package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.MessageResponse
import com.exchangemingle.backend.service.FileStorageService
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/files")
class FileUploadController(
    private val fileStorageService: FileStorageService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    @PostMapping("/upload/avatar")
    fun uploadAvatar(
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<Map<String, String>> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        // Delete old avatar if exists
        user.avatar?.let { oldAvatar ->
            fileStorageService.deleteAvatar(oldAvatar)
        }

        // Upload new avatar
        val avatarUrl = fileStorageService.uploadAvatar(file)

        // Update user avatar
        user.avatar = avatarUrl
        userService.updateProfile(user.id, com.exchangemingle.backend.dto.UpdateProfileRequest(avatar = avatarUrl))

        return ResponseEntity.ok(
            mapOf(
                "message" to "Avatar uploaded successfully",
                "avatarUrl" to avatarUrl
            )
        )
    }

    @DeleteMapping("/avatar")
    fun deleteAvatar(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<MessageResponse> {
        val token = authHeader.substring(7)
        val email = jwtService.extractUsername(token)
        val user = userService.findByEmail(email)

        user.avatar?.let { avatarUrl ->
            fileStorageService.deleteAvatar(avatarUrl)
            user.avatar = null
            userService.updateProfile(user.id, com.exchangemingle.backend.dto.UpdateProfileRequest(avatar = null))
        }

        return ResponseEntity.ok(MessageResponse("Avatar deleted successfully"))
    }
}