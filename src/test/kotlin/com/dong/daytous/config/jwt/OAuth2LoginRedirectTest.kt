package com.dong.daytous.config.jwt

import com.dong.daytous.config.encrypt.TokenEncryptor
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.repository.GoogleTokenRepository
import com.dong.daytous.repository.UserRepository
import com.dong.daytous.service.LoginCodeService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import java.net.URI
import java.util.Optional

class OAuth2LoginRedirectTest {
    @Test
    fun `로그인 리다이렉트에 JWT 대신 fragment 코드와 안전한 쿠키를 설정한다`() {
        val codes = mock<LoginCodeService>()
        val users = mock<UserRepository>()
        val user = User(id = 10L, name = "Test", email = "test@example.com",
            role = Role.USER, provider = "google", providerId = "test")
        whenever(users.findByEmail(user.email)).thenReturn(Optional.of(user))
        whenever(codes.issue(user.id)).thenReturn("one-use-code" to "browser-binding")
        val handler = OAuth2AuthenticationSuccessHandler(codes, users, mock<GoogleTokenRepository>(),
            mock<OAuth2AuthorizedClientService>(), mock<TokenEncryptor>(),
            "https://app.example.com/oauth2/redirect?old=value")
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val principal = DefaultOAuth2User(authorities, mapOf("email" to user.email), "email")
        val request = MockHttpServletRequest().apply { isSecure = true }
        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(request, response, OAuth2AuthenticationToken(principal, authorities, "google"))
        val target = URI(response.redirectedUrl!!)
        assertThat(target.rawQuery).isNull()
        assertThat(target.fragment).isEqualTo("code=one-use-code")
        assertThat(response.getHeader("Set-Cookie"))
            .contains("HttpOnly", "Secure", "SameSite=Lax", "Max-Age=60", "Path=/auth/login")
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store")
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer")
    }
}
