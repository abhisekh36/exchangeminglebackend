package com.exchangemingle.backend.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * Without this, Spring Security's default behavior for a missing/invalid
 * JWT on a protected endpoint is to return 403 Forbidden. That's wrong for
 * an API like this one: 403 means "authenticated but not allowed," while
 * 401 means "not authenticated at all" — which is what actually happened.
 *
 * This distinction matters beyond correctness: the Android client's
 * RetrofitClient uses an OkHttp Authenticator to automatically refresh an
 * expired/invalid access token and retry the request — but OkHttp only
 * ever invokes that Authenticator on a 401 response, never on 403. Without
 * this entry point, every expired or invalidated token (e.g. after a
 * JWT_SECRET rotation) permanently breaks every screen until the user
 * manually logs out and back in, instead of silently refreshing.
 */
@Component
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED // 401, not 403
        response.contentType = "application/json"
        response.writer.write(
            """{"timestamp":"${java.time.LocalDateTime.now()}","status":401,"error":"Unauthorized","message":"Full authentication is required to access this resource"}"""
        )
    }
}