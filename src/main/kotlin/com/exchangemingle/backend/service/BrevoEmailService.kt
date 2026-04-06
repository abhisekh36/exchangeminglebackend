package com.exchangemingle.backend.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class BrevoEmailService(
    private val webClient: WebClient.Builder
) {

    private val logger = LoggerFactory.getLogger(BrevoEmailService::class.java)

    @Value("\${brevo.api.key}")
    private lateinit var apiKey: String

    @Value("\${brevo.sender.email}")
    private lateinit var senderEmail: String

    @Value("\${brevo.sender.name}")
    private lateinit var senderName: String

    companion object {
        private const val BREVO_API_URL = "https://api.brevo.com/v3/smtp/email"
    }

    fun sendVerificationEmail(toEmail: String, toName: String, verificationCode: String) {
        val subject = "Verify Your Email - ExchangeMingle"
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .code { font-size: 32px; font-weight: bold; color: #667eea; text-align: center; padding: 20px; background: white; border-radius: 8px; letter-spacing: 5px; margin: 20px 0; }
                    .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Welcome to ExchangeMingle! 🎉</h1>
                    </div>
                    <div class="content">
                        <p>Hi <strong>$toName</strong>,</p>
                        <p>Thank you for registering with ExchangeMingle! Please verify your email address by entering the code below:</p>
                        <div class="code">$verificationCode</div>
                        <p>This code will expire in <strong>15 minutes</strong>.</p>
                        <p>If you didn't create an account, please ignore this email.</p>
                        <p>Best regards,<br>The ExchangeMingle Team</p>
                    </div>
                    <div class="footer">
                        <p>© ${java.time.Year.now().value} ExchangeMingle. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendEmail(toEmail, toName, subject, htmlContent)
    }

    fun sendPasswordResetEmail(toEmail: String, toName: String, resetToken: String) {
        val subject = "Reset Your Password - ExchangeMingle"
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .token { font-size: 14px; font-family: monospace; color: #333; background: white; padding: 15px; border-radius: 8px; margin: 20px 0; word-break: break-all; border: 2px dashed #f5576c; }
                    .warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; }
                    .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔐 Password Reset Request</h1>
                    </div>
                    <div class="content">
                        <p>Hi <strong>$toName</strong>,</p>
                        <p>We received a request to reset your password. Use the token below to reset your password:</p>
                        <div class="token">$resetToken</div>
                        <p>This token will expire in <strong>1 hour</strong>.</p>
                        <div class="warning">
                            <strong>⚠️ Security Notice:</strong> If you didn't request a password reset, please ignore this email and ensure your account is secure.
                        </div>
                        <p>Best regards,<br>The ExchangeMingle Team</p>
                    </div>
                    <div class="footer">
                        <p>© ${java.time.Year.now().value} ExchangeMingle. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendEmail(toEmail, toName, subject, htmlContent)
    }

    fun sendPasswordChangedEmail(toEmail: String, toName: String) {
        val subject = "Password Changed Successfully - ExchangeMingle"
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .success { background: #d4edda; border-left: 4px solid #28a745; padding: 15px; margin: 20px 0; }
                    .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>✅ Password Changed Successfully</h1>
                    </div>
                    <div class="content">
                        <p>Hi <strong>$toName</strong>,</p>
                        <div class="success">
                            Your password has been changed successfully!
                        </div>
                        <p>If you didn't make this change, please contact our support team immediately.</p>
                        <p>Best regards,<br>The ExchangeMingle Team</p>
                    </div>
                    <div class="footer">
                        <p>© ${java.time.Year.now().value} ExchangeMingle. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendEmail(toEmail, toName, subject, htmlContent)
    }

    private fun sendEmail(toEmail: String, toName: String, subject: String, htmlContent: String) {
        try {
            val requestBody = mapOf(
                "sender" to mapOf(
                    "name" to senderName,
                    "email" to senderEmail
                ),
                "to" to listOf(
                    mapOf(
                        "email" to toEmail,
                        "name" to toName
                    )
                ),
                "subject" to subject,
                "htmlContent" to htmlContent
            )

            val client = webClient
                .baseUrl(BREVO_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("api-key", apiKey)
                .build()

            val response = client.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block()

            logger.info("Email sent successfully to $toEmail. MessageId: ${response?.get("messageId")}")

        } catch (e: Exception) {
            logger.error("Failed to send email to $toEmail", e)
            // Don't throw exception - email failure shouldn't break the flow
        }
    }
}