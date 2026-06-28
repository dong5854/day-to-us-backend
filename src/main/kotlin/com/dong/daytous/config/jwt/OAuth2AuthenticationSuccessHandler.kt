package com.dong.daytous.config.jwt

import com.dong.daytous.config.encrypt.TokenEncryptor
import com.dong.daytous.domain.user.GoogleToken
import com.dong.daytous.repository.GoogleTokenRepository
import com.dong.daytous.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository,
    private val googleTokenRepository: GoogleTokenRepository,
    private val authorizedClientService: OAuth2AuthorizedClientService,
    private val tokenEncryptor: TokenEncryptor,
    @Value("\${oauth2.redirect-uri}") private val redirectUri: String,
) : SimpleUrlAuthenticationSuccessHandler() {

    @Transactional
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oAuth2Token = authentication as OAuth2AuthenticationToken
        val principal = oAuth2Token.principal
            ?: throw IllegalStateException("OAuth2 principal is null")
        val email = principal.attributes["email"] as String

        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow { IllegalArgumentException("User not found with email: $email") }

        saveGoogleTokens(oAuth2Token, user.id)

        val token = jwtTokenProvider.createToken(user)
        val targetUrl =
            UriComponentsBuilder
                .fromUriString(redirectUri)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString()

        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, targetUrl)
    }

    private fun saveGoogleTokens(oAuth2Token: OAuth2AuthenticationToken, userId: Long) {
        val authorizedClient: OAuth2AuthorizedClient =
            authorizedClientService.loadAuthorizedClient(
                oAuth2Token.authorizedClientRegistrationId,
                oAuth2Token.name,
            ) ?: return

        val accessTokenValue = authorizedClient.accessToken.tokenValue
        val refreshTokenValue = authorizedClient.refreshToken?.tokenValue ?: return
        val expiresAt =
            authorizedClient.accessToken.expiresAt
                ?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()) }
                ?: LocalDateTime.now().plusHours(1)

        val user =
            userRepository.findById(userId)
                .orElseThrow { IllegalArgumentException("User not found with id: $userId") }

        val googleToken =
            googleTokenRepository.findByUserId(userId)
                .map { existing ->
                    existing.accessToken = tokenEncryptor.encrypt(accessTokenValue)
                    existing.refreshToken = tokenEncryptor.encrypt(refreshTokenValue)
                    existing.expiresAt = expiresAt
                    existing
                }
                .orElseGet {
                    GoogleToken(
                        user = user,
                        accessToken = tokenEncryptor.encrypt(accessTokenValue),
                        refreshToken = tokenEncryptor.encrypt(refreshTokenValue),
                        expiresAt = expiresAt,
                    )
                }

        googleTokenRepository.save(googleToken)
    }
}
