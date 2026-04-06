package com.exchangemingle.backend.service

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.util.*

@Service
class FileStorageService(
    private val cloudinary: Cloudinary
) {

    private val logger = LoggerFactory.getLogger(FileStorageService::class.java)

    companion object {
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/jpg", "image/webp")
        private const val AVATAR_FOLDER = "avatars"
    }

    /**
     * Upload avatar to Cloudinary
     * Returns the secure URL of the uploaded image
     */
    fun uploadAvatar(file: MultipartFile): String {
        // Validate file
        validateFile(file)

        try {
            // Generate unique filename
            val publicId = "$AVATAR_FOLDER/${UUID.randomUUID()}"

            // Upload to Cloudinary with transformations
            val uploadResult = cloudinary.uploader().upload(
                file.bytes,
                ObjectUtils.asMap(
                    "public_id", publicId,
                    "folder", AVATAR_FOLDER,
                    "resource_type", "image",
                    "transformation", arrayOf(
                        ObjectUtils.asMap(
                            "width", 400,
                            "height", 400,
                            "crop", "fill",
                            "gravity", "face",
                            "quality", "auto:good",
                            "fetch_format", "auto"
                        )
                    )
                )
            )

            val secureUrl = uploadResult["secure_url"] as String
            logger.info("Successfully uploaded avatar: $secureUrl")
            return secureUrl

        } catch (e: IOException) {
            logger.error("Failed to upload avatar to Cloudinary", e)
            throw RuntimeException("Failed to upload file: ${e.message}")
        }
    }

    /**
     * Delete avatar from Cloudinary
     */
    fun deleteAvatar(imageUrl: String): Boolean {
        return try {
            // Extract public_id from URL
            val publicId = extractPublicIdFromUrl(imageUrl)
            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())
                logger.info("Successfully deleted avatar: $publicId")
                true
            } else {
                logger.warn("Could not extract public_id from URL: $imageUrl")
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to delete avatar from Cloudinary", e)
            false
        }
    }

    /**
     * Validate uploaded file
     */
    private fun validateFile(file: MultipartFile) {
        // Check if file is empty
        if (file.isEmpty) {
            throw IllegalArgumentException("File is empty")
        }

        // Check file size
        if (file.size > MAX_FILE_SIZE) {
            throw IllegalArgumentException("File size exceeds maximum limit of 5MB")
        }

        // Check file type
        val contentType = file.contentType
        if (contentType !in ALLOWED_IMAGE_TYPES) {
            throw IllegalArgumentException("Invalid file type. Only JPEG, PNG, and WebP images are allowed")
        }
    }

    /**
     * Extract public_id from Cloudinary URL
     */
    private fun extractPublicIdFromUrl(url: String): String? {
        return try {
            // URL format: https://res.cloudinary.com/{cloud_name}/image/upload/v{version}/{folder}/{filename}.{ext}
            val regex = "$AVATAR_FOLDER/[^.]+".toRegex()
            regex.find(url)?.value
        } catch (e: Exception) {
            null
        }
    }
}