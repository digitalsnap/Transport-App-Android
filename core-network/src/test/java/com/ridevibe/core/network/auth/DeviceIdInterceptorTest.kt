package com.ridevibe.core.network.auth

import com.ridevibe.core.network.storage.InMemoryKeyValueStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeviceIdInterceptorTest {

    private val server = MockWebServer()
    private val identity = InMemoryKeyValueStore()
    private val currentUserId = CurrentUserId(identity)
    private val client = OkHttpClient.Builder()
        .addInterceptor(DeviceIdInterceptor(DeviceIdProvider(identity), currentUserId))
        .build()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun call(path: String, response: MockResponse = MockResponse().setResponseCode(204)) {
        server.enqueue(response)
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
    }

    @Test
    fun `passenger routes carry the device id and echo the user id back`() {
        call("/v1/trips/T1/seats/1A/lock", MockResponse().setResponseCode(204).addHeader("X-User-Id", "u-42"))

        val recorded = server.takeRequest()
        assertNotNull(recorded.getHeader("X-Device-Id"))
        assertEquals(identity.get("device_id"), recorded.getHeader("X-Device-Id"))
        assertEquals("u-42", currentUserId.value)
        assertEquals("u-42", identity.get("user_id")) // survives process death
    }

    @Test
    fun `staff routes never see the passenger identity`() {
        call("/admin/api/overview", MockResponse().setResponseCode(200).addHeader("X-User-Id", "should-be-ignored"))
        assertNull(server.takeRequest().getHeader("X-Device-Id"))
        assertNull(currentUserId.value)

        call("/auth/me")
        assertNull(server.takeRequest().getHeader("X-Device-Id"))

        call("/partner/api/me")
        assertNull(server.takeRequest().getHeader("X-Device-Id"))
    }

    @Test
    fun `device id is stable across calls and clients`() {
        call("/v1/locations")
        call("/v1/bus-classes")
        val first = server.takeRequest().getHeader("X-Device-Id")
        val second = server.takeRequest().getHeader("X-Device-Id")
        assertEquals(first, second)
        assertEquals(first, DeviceIdProvider(identity).deviceId)
    }

    @Test
    fun `current user id restores from the store and clears`() {
        identity.put("user_id", "u-old")
        val restored = CurrentUserId(identity)
        assertEquals("u-old", restored.value)

        restored.update("") // blank never overwrites
        assertEquals("u-old", restored.value)

        restored.clear()
        assertNull(restored.value)
        assertNull(identity.get("user_id"))
    }
}
