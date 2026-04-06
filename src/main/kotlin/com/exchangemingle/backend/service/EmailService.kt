package com.exchangemingle.backend.service

import org.springframework.stereotype.Service

@Service
class EmailService(
    private val brevoEmailService: BrevoEmailService
) {

    fun sendVerificationEmail(email: String, code: String) {
        // Extract name from email (before @)
        val name = email.substringBefore("@").split(".").joinToString(" ") {
            it.replaceFirstChar { char -> char.uppercase() }
        }
        brevoEmailService.sendVerificationEmail(email, name, code)
    }

    fun sendPasswordResetEmail(email: String, resetToken: String) {
        val name = email.substringBefore("@").split(".").joinToString(" ") {
            it.replaceFirstChar { char -> char.uppercase() }
        }
        brevoEmailService.sendPasswordResetEmail(email, name, resetToken)
    }

    fun sendPasswordChangedEmail(email: String, name: String) {
        brevoEmailService.sendPasswordChangedEmail(email, name)
    }
}