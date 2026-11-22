package com.dong.daytous.config

import com.dong.daytous.domain.user.TokenAccess
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

// Each entry is an explicit external API operation. Newly added app routes remain private.
object PersonalTokenRoutes {
    fun isPersonalToken() = SecurityContextHolder.getContext().authentication?.authorities.orEmpty().any { it.authority == "PERSONAL_TOKEN" }

    fun requireGeneralSavings(isGeneral: Boolean) {
        if (!isGeneral && isPersonalToken()) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private const val ID = "[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}"
    private val spacePath = Regex("/shared-spaces/($ID)/(.+)")
    private const val COLLECTION = "(?:budget-entries|schedules|savings|fixed-expenses|expense-categories|payment-methods)"
    private val operations = mapOf(
        "GET" to Regex("$COLLECTION|(?:budget-entries|schedules)/$ID|schedules/conflicts"),
        "POST" to Regex("$COLLECTION|(?:savings|fixed-expenses)/$ID/preview|savings/$ID/records|schedules/$ID/resolve-conflict"),
        "PUT" to Regex("(?:budget-entries|schedules|savings|fixed-expenses)/$ID|savings/$ID/records/$ID"),
        "DELETE" to Regex("$COLLECTION/$ID|savings/$ID/records/$ID"),
    )

    fun resolve(method: String, path: String): Pair<UUID, TokenAccess>? {
        val (space, operation) = spacePath.matchEntire(path)?.destructured ?: return null
        if (operations[method]?.matches(operation) != true) return null
        return UUID.fromString(space) to if (method == "GET") TokenAccess.READ else TokenAccess.EDIT
    }
}
