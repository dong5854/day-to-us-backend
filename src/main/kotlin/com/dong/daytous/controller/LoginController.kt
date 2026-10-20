package com.dong.daytous.controller

import com.dong.daytous.service.LoginCodeService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class LoginExchangeRequest(val code: String)
data class LoginExchangeResponse(val accessToken: String)

@RestController
class LoginController(private val loginCodes: LoginCodeService) {
    @PostMapping("/auth/login/exchange")
    fun exchange(
        @RequestBody body: LoginExchangeRequest,
        @CookieValue(name = LoginCodeService.COOKIE_NAME, defaultValue = "") binding: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): LoginExchangeResponse {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        val token = loginCodes.exchange(body.code, binding)
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(LoginCodeService.COOKIE_NAME, "")
            .httpOnly(true).secure(request.isSecure).sameSite("Lax")
            .path(LoginCodeService.COOKIE_PATH).maxAge(0).build().toString())
        return LoginExchangeResponse(token)
    }
}
