package com.exchangemingle.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {

    @Bean
    fun corsFilter(): CorsFilter {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration()

        // Allow credentials
        config.allowCredentials = true

        // Allow all origins (for development - restrict in production)
        config.addAllowedOriginPattern("*")

        // Allow common headers
        config.addAllowedHeader("Authorization")
        config.addAllowedHeader("Content-Type")
        config.addAllowedHeader("Accept")
        config.addAllowedHeader("X-Requested-With")

        // Allow common methods
        config.addAllowedMethod("GET")
        config.addAllowedMethod("POST")
        config.addAllowedMethod("PUT")
        config.addAllowedMethod("DELETE")
        config.addAllowedMethod("PATCH")
        config.addAllowedMethod("OPTIONS")

        // Apply CORS to all API endpoints
        source.registerCorsConfiguration("/api/**", config)

        return CorsFilter(source)
    }
}