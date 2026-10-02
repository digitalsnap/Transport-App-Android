package com.ridevibe.core.network.auth

import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.domain.model.StaffSession
import com.ridevibe.core.network.storage.InMemoryKeyValueStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StaffTokenInterceptorTest {

    private val server = MockWebServer()
    private val prefs = InMemoryKeyValueStore()
    private val sessionStore = StaffSessionStore(prefs)
    private val client = OkHttpClient.Builder()
        .addInterceptor(StaffTokenInterceptor(sessionStore))
        .build()

    private val admin = StaffSession(token = "s_admin", email = "admin@admin.com", role = StaffRole.ADMIN)
    private val partner = StaffSession(
        token = "s_partner",
        email = "partner@partner.com",
        role = StaffRole.PARTNER,
        operatorId = 1,
        operatorName = "Victory Liner",
        expiresAtEpochMillis = 9_999_999_999_999L,
    )

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun call(path: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code))
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
    }

    @Test
    fun `admin sessions send x-admin-token on staff routes only`() {
        sessionStore.save(admin)

        call("/admin/api/overview")
        val staff = server.takeRequest()
        assertEquals("s_admin", staff.getHeader("x-admin-token"))
        assertNull(staff.getHeader("x-partner-token"))

        call("/v1/trips")
        val passenger = server.takeRequest()
        assertNull(passenger.getHeader("x-admin-token"))
        assertNull(passenger.getHeader("x-partner-token"))
    }

    @Test
    fun `partner sessions send x-partner-token`() {
        sessionStore.save(partner)
        call("/partner/api/me")
        val recorded = server.takeRequest()
        assertEquals("s_partner", recorded.getHeader("x-partner-token"))
        assertNull(recorded.getHeader("x-admin-token"))
    }

    @Test
    fun `signed out means no header at all`() {
        call("/auth/me")
        assertNull(server.takeRequest().getHeader("x-admin-token"))
    }

    @Test
    fun `401 on admin or partner clears the session that was rejected`() {
        sessionStore.save(admin)
        call("/admin/api/accounts", code = 401)
        assertNull(sessionStore.session.value)

        sessionStore.save(partner)
        call("/partner/api/trips", code = 401)
        assertNull(sessionStore.session.value)
    }

    @Test
    fun `401 on auth-me is left to the repository`() {
        sessionStore.save(admin)
        call("/auth/me", code = 401)
        assertNotNull(sessionStore.session.value)
    }

    @Test
    fun `a sign-in that raced a stale 401 is not wiped`() {
        sessionStore.save(admin)
        server.enqueue(MockResponse().setResponseCode(401))
        // The 401 comes back for s_admin, but by then a fresh session is stored.
        val request = Request.Builder().url(server.url("/admin/api/overview")).build()
        val fresh = admin.copy(token = "s_fresh")
        client.newBuilder().addNetworkInterceptor { chain ->
            sessionStore.save(fresh)
            chain.proceed(chain.request())
        }.build().newCall(request).execute().close()

        assertEquals("s_fresh", sessionStore.session.value?.token)
    }

    @Test
    fun `session store persists expiry and reports it`() {
        sessionStore.save(partner)
        val reloaded = StaffSessionStore(prefs)
        assertEquals(partner, reloaded.session.value)
        assertFalse(reloaded.isExpired())

        sessionStore.save(partner.copy(expiresAtEpochMillis = 1L))
        assertTrue(StaffSessionStore(prefs).isExpired())

        sessionStore.clear()
        assertNull(StaffSessionStore(prefs).session.value)
        assertTrue(prefs.snapshot().isEmpty())
    }
}
