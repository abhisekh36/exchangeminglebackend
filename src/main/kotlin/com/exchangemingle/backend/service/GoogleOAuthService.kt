package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.GoogleUserInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GoogleOAuthService {

    private val logger = LoggerFactory.getLogger(GoogleOAuthService::class.java)

    /**
     * Verifies a Firebase ID token sent from the Android app.
     *
     * The Android frontend authenticates with Google via Firebase, then calls
     * firebaseUser.getIdToken() to get a Firebase ID token — NOT a raw Google OAuth token.
     * Firebase Admin SDK is the correct verifier for this token type.
     */
    fun verifyIdToken(idToken: String): GoogleUserInfo? {
        return try {
            val decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken)

            GoogleUserInfo(
                sub = decodedToken.uid,
                email = decodedToken.email ?: run {
                    logger.warn("Firebase token missing email for uid: ${decodedToken.uid}")
                    return null
                },
                emailVerified = decodedToken.isEmailVerified,
                name = decodedToken.name,
                picture = decodedToken.picture,
                givenName = null,
                familyName = null
            )
        } catch (e: FirebaseAuthException) {
            logger.warn("Firebase token verification failed: ${e.authErrorCode} - ${e.message}")
            null
        } catch (e: Exception) {
            logger.error("Unexpected error during Firebase token verification", e)
            null
        }
    }
}