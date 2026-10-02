package com.ridevibe.feature.admin.viewmodel

import com.ridevibe.core.network.api.CrsApiException

/**
 * The line staff see when a call fails. Only [CrsApiException] carries text
 * that was written for people (the server's own `message`); anything else —
 * a malformed body, a serialization error — falls back to [fallback] so a
 * stack-trace-style message never lands on a partner's phone.
 */
internal fun Throwable.staffMessage(fallback: String): String = when (this) {
    is CrsApiException -> when (code) {
        CrsApiException.OFFLINE_CODE -> "You're offline — check the connection and try again"
        401 -> "Session expired — sign in again"
        403 -> "Read-only access — your account cannot make this change"
        429 -> "Too many requests, try again shortly"
        else -> message.ifBlank { fallback }
    }
    else -> fallback
}
