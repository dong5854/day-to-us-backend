package com.dong.daytous.controller

import com.dong.daytous.exception.GlobalExceptionHandler
import com.dong.daytous.service.LoginCodeService
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LoginControllerTest {
    private val service = mock<LoginCodeService>()
    private val mvc = MockMvcBuilders.standaloneSetup(LoginController(service))
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @Test
    fun `토큰은 캐시되지 않는 본문으로 전달하고 바인딩 쿠키를 지운다`() {
        whenever(service.exchange("code", "binding")).thenReturn("test-jwt")
        mvc.perform(post("/auth/login/exchange").secure(true)
            .cookie(Cookie(LoginCodeService.COOKIE_NAME, "binding"))
            .contentType("application/json").content("""{"code":"code"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("test-jwt"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(cookie().httpOnly(LoginCodeService.COOKIE_NAME, true))
            .andExpect(cookie().secure(LoginCodeService.COOKIE_NAME, true))
            .andExpect(cookie().maxAge(LoginCodeService.COOKIE_NAME, 0))
    }

    @Test
    fun `쿠키가 없는 요청은 토큰 없이 실패한다`() {
        whenever(service.exchange("code", "")).thenThrow(IllegalArgumentException("다시 로그인해 주세요."))
        mvc.perform(post("/auth/login/exchange").contentType("application/json").content("""{"code":"code"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(header().string("Cache-Control", "no-store"))
    }
}
