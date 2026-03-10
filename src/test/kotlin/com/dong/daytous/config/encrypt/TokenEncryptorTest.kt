package com.dong.daytous.config.encrypt

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class TokenEncryptorTest {

    private val validKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val encryptor = TokenEncryptor(validKey)

    @Test
    fun `암호화 후 복호화하면 원본 텍스트를 반환한다`() {
        val plainText = "ya29.a0AfH6SMBx-test-access-token"

        val encrypted = encryptor.encrypt(plainText)
        val decrypted = encryptor.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plainText)
    }

    @Test
    fun `빈 문자열을 암호화하고 복호화할 수 있다`() {
        val plainText = ""

        val encrypted = encryptor.encrypt(plainText)
        val decrypted = encryptor.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plainText)
    }

    @Test
    fun `같은 텍스트를 두 번 암호화하면 서로 다른 암호문을 생성한다`() {
        val plainText = "test-token"

        val encrypted1 = encryptor.encrypt(plainText)
        val encrypted2 = encryptor.encrypt(plainText)

        assertThat(encrypted1).isNotEqualTo(encrypted2)
    }

    @Test
    fun `긴 토큰도 암호화하고 복호화할 수 있다`() {
        val longToken = "a".repeat(2048)

        val encrypted = encryptor.encrypt(longToken)
        val decrypted = encryptor.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(longToken)
    }

    @Test
    fun `다른 키로 복호화하면 실패한다`() {
        val otherKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 100).toByte() })
        val otherEncryptor = TokenEncryptor(otherKey)

        val encrypted = encryptor.encrypt("secret-token")

        assertThatThrownBy {
            otherEncryptor.decrypt(encrypted)
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `한글 토큰도 암호화하고 복호화할 수 있다`() {
        val plainText = "한글테스트토큰"

        val encrypted = encryptor.encrypt(plainText)
        val decrypted = encryptor.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plainText)
    }
}
