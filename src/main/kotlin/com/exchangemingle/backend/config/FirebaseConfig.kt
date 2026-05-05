package com.exchangemingle.backend.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.util.*
import jakarta.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Value("\${firebase.service.account.base64:}")
    private var serviceAccountBase64: String? = null

    @Value("\${firebase.service.account.path:}")
    private var serviceAccountPath: String? = null

    @PostConstruct
    fun initialize() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                val credentials = when {
                    // Option 1: Load from base64 environment variable (PRODUCTION)
                    !serviceAccountBase64.isNullOrEmpty() -> {
                        logger.info("Loading Firebase credentials from base64 environment variable")
                        val decodedBytes = Base64.getDecoder().decode(serviceAccountBase64)
                        GoogleCredentials.fromStream(ByteArrayInputStream(decodedBytes))
                    }

                    // Option 2: Load from file path (DEVELOPMENT)
                    !serviceAccountPath.isNullOrEmpty() -> {
                        logger.info("Loading Firebase credentials from file: $serviceAccountPath")
                        GoogleCredentials.fromStream(FileInputStream(serviceAccountPath))
                    }

                    // Option 3: Load from classpath (ALTERNATIVE)
                    else -> {
                        logger.info("Loading Firebase credentials from classpath")
                        val resource = ClassPathResource("firebase-service-account.json")
                        if (resource.exists()) {
                            GoogleCredentials.fromStream(resource.inputStream)
                        } else {
                            throw IllegalStateException("Firebase service account credentials not found!")
                        }
                    }
                }

                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build()

                FirebaseApp.initializeApp(options)
                logger.info("✅ Firebase initialized successfully!")

            } catch (e: Exception) {
                logger.error("❌ Failed to initialize Firebase", e)
                throw RuntimeException("Failed to initialize Firebase: ${e.message}", e)
            }
        } else {
            logger.info("Firebase already initialized")
        }
    }
}