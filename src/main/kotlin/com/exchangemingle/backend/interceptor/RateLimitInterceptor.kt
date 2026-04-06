package com.exchangemingle.backend.interceptor

import com.exchangemingle.backend.service.RateLimitService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class RateLimitInterceptor(
    private val rateLimitService: RateLimitService
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val clientIp = getClientIp(request)
        val path = request.requestURI

        val bucket = when {
            path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register") -> {
                rateLimitService.resolveAuthBucket("auth:$clientIp")
            }

            path.startsWith("/api/auth/verify-email") || path.startsWith("/api/auth/resend-verification") ||
                    path.startsWith("/api/auth/forgot-password") -> {
                rateLimitService.resolveEmailBucket("email:$clientIp")
            }

            else -> {
                rateLimitService.resolveApiBucket("api:$clientIp")
            }
        }

        val probe = bucket.tryConsumeAndReturnRemaining(1)

        return if (probe.isConsumed) {
            response.addHeader("X-Rate-Limit-Remaining", probe.remainingTokens.toString())
            true
        } else {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write(
                """
                {
                    "error": "Too Many Requests",
                    "message": "Rate limit exceeded. Please try again later.",
                    "retryAfter": ${probe.nanosToWaitForRefill / 1_000_000_000}
                }
                """.trimIndent()
            )
            false
        }
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (xForwardedFor.isNullOrEmpty()) {
            request.remoteAddr
        } else {
            xForwardedFor.split(",")[0].trim()
        }
    }
}