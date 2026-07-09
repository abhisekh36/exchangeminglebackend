package com.exchangemingle.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

// Cross-Origin Resource Sharing config.
// Mobile apps (Android) don't send an Origin header, so CORS doesn't
// actually apply to them. This config exists for your web frontend /
// Swagger (now disabled in prod) / any browser-based clients.
@Configuration
class CorsConfig {

    @Bean
    fun corsFilter(): CorsFilter {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration()

        // Only allow your actual domain + localhost for dev.
        // Mobile apps bypass CORS entirely (no Origin header sent).
        config.allowedOrigins = listOf(
            "https://exchangemingle.com",
            "https://www.exchangemingle.com",
            "http://localhost:3000",   // local web dev
            "http://localhost:8080"    // local Spring dev
        )

        // Don't send cookies / credentials cross-origin
        // (JWT is in Authorization header, not a cookie)
        config.allowCredentials = false

        config.allowedHeaders = listOf(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With"
        )

        config.allowedMethods = listOf(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        )

        // Cache preflight response for 1 hour
        config.maxAge = 3600L

        source.registerCorsConfiguration("/api/**", config)
        return CorsFilter(source)
    }
}