package com.exchangemingle.backend.security

import com.exchangemingle.backend.service.JwtService
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val jwt = extractJwtFromRequest(request)

            if (jwt != null && SecurityContextHolder.getContext().authentication == null) {
                val userEmail = jwtService.extractUsername(jwt)
                val userDetails = userDetailsService.loadUserByUsername(userEmail)

                if (jwtService.validateToken(jwt, userDetails)) {
                    val authToken = UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.authorities
                    )
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authToken
                }
            }
        } catch (e: ExpiredJwtException) {
            // Expected, routine condition — a client is simply using a token past
            // its 24h expiry, which happens constantly under normal use and isn't
            // an application error. Logging this at ERROR with a full ~90-line
            // Spring Security filter-chain stack trace, on every single request
            // that carries a stale token, was pure waste: extra CPU building the
            // trace, extra log I/O, and extra noise burying real errors. The
            // request already proceeds unauthenticated either way (filter chain
            // continues below), so nothing about behavior changes — just the log.
            logger.debug("JWT expired for request to ${request.requestURI}")
        } catch (e: JwtException) {
            // Malformed/invalid signature etc. — also routine (bad/corrupted
            // client token), same reasoning as above, one line instead of a trace.
            logger.debug("Invalid JWT for request to ${request.requestURI}: ${e.message}")
        } catch (e: Exception) {
            // Anything else is unexpected — keep the full trace for these.
            logger.error("Cannot set user authentication", e)
        }

        filterChain.doFilter(request, response)
    }

    private fun extractJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else null
    }
}