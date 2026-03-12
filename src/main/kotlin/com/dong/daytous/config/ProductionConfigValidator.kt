package com.dong.daytous.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.util.Base64

@Component
@Profile("prod")
class ProductionConfigValidator(
    private val environment: Environment,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun validateProductionConfig() {
        val errors = mutableListOf<String>()

        // Required environment variables
        val requiredEnvVars = listOf(
            "PROD_DB_URL" to "PostgreSQL database URL",
            "PROD_DB_USERNAME" to "Database username",
            "PROD_DB_PASSWORD" to "Database password",
            "GOOGLE_CLIENT_ID" to "Google OAuth2 client ID",
            "GOOGLE_CLIENT_SECRET" to "Google OAuth2 client secret",
            "JWT_SECRET_KEY" to "JWT signing key",
            "JWT_EXPIRATION_TIME" to "JWT expiration time",
            "TOKEN_ENCRYPTION_KEY" to "AES-256 encryption key for OAuth tokens",
            "PROD_CORS_ALLOWED_ORIGINS" to "CORS allowed origins",
            "PROD_OAUTH_REDIRECT_URI" to "OAuth2 redirect URI",
        )

        for ((envVar, description) in requiredEnvVars) {
            val value = System.getenv(envVar)
            if (value.isNullOrBlank()) {
                errors.add("$envVar ($description) is not set")
            }
        }

        // Validate TOKEN_ENCRYPTION_KEY format (must be valid Base64, 32 bytes)
        val encryptionKey = environment.getProperty("token.encryption.key")
        if (!encryptionKey.isNullOrBlank()) {
            try {
                val keyBytes = Base64.getDecoder().decode(encryptionKey)
                if (keyBytes.size != 32) {
                    errors.add("TOKEN_ENCRYPTION_KEY must be 256-bit (32 bytes), got ${keyBytes.size} bytes")
                }
            } catch (e: IllegalArgumentException) {
                errors.add("TOKEN_ENCRYPTION_KEY is not valid Base64")
            }
        }

        // Validate Google Calendar scope is configured
        val scope = environment.getProperty("spring.security.oauth2.client.registration.google.scope")
        if (scope != null && !scope.contains("calendar")) {
            log.warn("[PRODUCTION] Google Calendar scope is missing from OAuth2 configuration. Calendar sync will not work.")
        }

        if (errors.isNotEmpty()) {
            log.error("[PRODUCTION] Configuration validation failed:")
            errors.forEach { log.error("  - $it") }
            throw IllegalStateException("Production configuration validation failed. Fix the above errors before deploying.")
        }

        log.info("[PRODUCTION] All configuration validated successfully.")
    }
}
