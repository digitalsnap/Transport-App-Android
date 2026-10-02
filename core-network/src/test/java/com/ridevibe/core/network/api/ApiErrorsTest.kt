package com.ridevibe.core.network.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ApiErrorsTest {

    private fun httpError(code: Int, body: String? = null): HttpException =
        HttpException(Response.error<Any>(code, (body ?: "").toResponseBody("application/json".toMediaType())))

    private suspend fun failWith(error: Throwable): CrsApiException {
        val result = apiResult<Unit> { throw error }
        val exception = result.exceptionOrNull()
        assertTrue("expected CrsApiException, got $exception", exception is CrsApiException)
        return exception as CrsApiException
    }

    @Test
    fun `409 carries the server's own message`() = runTest {
        val error = failWith(httpError(409, """{"message":"Seat already held by another user"}"""))
        assertEquals(409, error.code)
        assertEquals("Seat already held by another user", error.message)
    }

    @Test
    fun `429 without a body gets the retry-shortly fallback`() = runTest {
        val error = failWith(httpError(429))
        assertEquals(429, error.code)
        assertEquals("Too many requests — try again shortly", error.message)
    }

    @Test
    fun `401 on the staff surface says sign-in required`() = runTest {
        val error = failWith(httpError(401, "not json at all"))
        assertEquals(401, error.code)
        assertEquals("Sign-in required", error.message)
    }

    @Test
    fun `401 on the passenger surface explains the device session instead`() = runTest {
        val result = passengerApiResult<Unit> { throw httpError(401, """{"message":"Missing X-Device-Id"}""") }
        val error = result.exceptionOrNull() as CrsApiException
        assertEquals(401, error.code)
        assertEquals(CrsApiException.DEVICE_SESSION_REJECTED_MESSAGE, error.message)
    }

    @Test
    fun `passenger wrapper leaves other codes untouched`() = runTest {
        val result = passengerApiResult<Unit> { throw httpError(404, """{"message":"No such trip"}""") }
        assertEquals("No such trip", (result.exceptionOrNull() as CrsApiException).message)
    }

    @Test
    fun `IOException is offline with code 0`() = runTest {
        val error = failWith(SocketTimeoutException("read timed out"))
        assertEquals(CrsApiException.OFFLINE_CODE, error.code)
        assertTrue(error.isOffline)
        assertEquals(CrsApiException.OFFLINE_MESSAGE, error.message)

        val plain = failWith(IOException("boom"))
        assertEquals(0, plain.code)
    }

    @Test
    fun `an already shaped CrsApiException passes through unchanged`() = runTest {
        val original = CrsApiException(503, "Google sign-in is not configured")
        assertTrue(failWith(original) === original)
    }

    @Test
    fun `unexpected exceptions become failures, not crashes`() = runTest {
        val result = apiResult<Unit> { throw IllegalStateException("malformed body") }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never swallowed`() = runTest {
        apiResult<Unit> { throw CancellationException("scope cancelled") }
    }

    @Test
    fun `success is wrapped as is`() = runTest {
        assertEquals(42, apiResult { 42 }.getOrThrow())
    }
}
