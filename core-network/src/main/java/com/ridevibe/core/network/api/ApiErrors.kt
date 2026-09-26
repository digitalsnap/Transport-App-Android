package com.ridevibe.core.network.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * A CRS call that did not succeed. [code] is the HTTP status (0 = no
 * connection at all) and [message] is human-readable — the server's own
 * `{ "message": "..." }` when it sent one, otherwise a short fallback per code.
 *
 * Extends IOException so existing `catch (e: IOException)` paths keep working;
 * the mock repositories throw it directly to mirror server refusals.
 */
class CrsApiException(val code: Int, override val message: String) : IOException(message) {
    /** No connection at all (DNS, timeout, airplane mode) — as opposed to a server refusal. */
    val isOffline: Boolean get() = code == OFFLINE_CODE

    companion object {
        const val OFFLINE_CODE = 0
        const val OFFLINE_MESSAGE = "No connection to the CRS"

        /**
         * A 401 on the passenger surface means the server no longer recognises
         * the device id it was sent (see DeviceIdProvider) — not that a sign-in
         * is required, which is what the staff fallback would wrongly say.
         */
        const val DEVICE_SESSION_REJECTED_MESSAGE =
            "Your session on this device was not recognised. Restart the app to sign in again."
    }
}

/**
 * Runs [block]; converts retrofit2.HttpException into CrsApiException using the
 * server's `{message}` body (fallback: a short human text per code: 401
 * "Sign-in required", 403 "Not allowed", 404 "Not found", 409 body-or-"Conflict",
 * 429 "Too many requests — try again shortly", else "Server error (code)"), and
 * IOException into CrsApiException(0, "No connection to the CRS").
 */
suspend fun <T> apiResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e // never swallow coroutine cancellation
} catch (e: CrsApiException) {
    Result.failure(e) // already shaped (mocks, or a nested apiResult)
} catch (e: HttpException) {
    Result.failure(e.toCrsApiException())
} catch (e: IOException) {
    Result.failure(CrsApiException(CrsApiException.OFFLINE_CODE, CrsApiException.OFFLINE_MESSAGE))
} catch (e: Exception) {
    // Malformed body etc. — surface as a failed Result rather than crashing a staff view.
    Result.failure(e)
}

/**
 * [apiResult] for the `/v1` passenger surface: identical, except a 401 is
 * reworded for a rider (there is no sign-in to redo — the device id itself
 * was rejected).
 */
suspend fun <T> passengerApiResult(block: suspend () -> T): Result<T> {
    val result = apiResult(block)
    val error = result.exceptionOrNull()
    return if (error is CrsApiException && error.code == 401) {
        Result.failure(CrsApiException(401, CrsApiException.DEVICE_SESSION_REJECTED_MESSAGE))
    } else {
        result
    }
}

@Serializable
private data class ErrorBodyDto(val message: String? = null)

/** Lenient on purpose: an error body is best-effort and must never itself throw. */
private val errorJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

internal fun HttpException.toCrsApiException(): CrsApiException {
    val serverMessage = runCatching {
        response()?.errorBody()?.string()
            ?.takeIf { it.isNotBlank() }
            ?.let { errorJson.decodeFromString(ErrorBodyDto.serializer(), it).message }
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
    return CrsApiException(code(), serverMessage ?: fallbackMessage(code()))
}

private fun fallbackMessage(code: Int): String = when (code) {
    401 -> "Sign-in required"
    403 -> "Not allowed"
    404 -> "Not found"
    409 -> "Conflict"
    429 -> "Too many requests — try again shortly"
    else -> "Server error ($code)"
}
