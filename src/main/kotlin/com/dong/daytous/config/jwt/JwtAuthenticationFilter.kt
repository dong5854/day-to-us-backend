package com.dong.daytous.config.jwt

import com.dong.daytous.config.PersonalTokenRoutes
import com.dong.daytous.service.PersonalTokenService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.server.ResponseStatusException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val personalTokens: PersonalTokenService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        if (token?.startsWith("dtu_") == true) {
            val grant = PersonalTokenRoutes.resolve(request.method, request.requestURI.removePrefix(request.contextPath))
            if (grant == null || token.length != 47) {
                response.status = if (grant == null) 403 else 401
                return
            }
            try {
                val email = personalTokens.authorize(token, grant.first, grant.second)
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken.authenticated(email, null, listOf(SimpleGrantedAuthority("PERSONAL_TOKEN")))
            } catch (e: ResponseStatusException) {
                response.status = e.statusCode.value()
                e.headers.forEach { name, values -> values.forEach { response.addHeader(name, it) } }
                return
            }
        } else if (token != null && jwtTokenProvider.validateToken(token)) {
            val authentication = jwtTokenProvider.getAuthentication(token)
            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }
}
