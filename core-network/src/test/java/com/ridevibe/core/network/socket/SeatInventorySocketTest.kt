package com.ridevibe.core.network.socket

import app.cash.turbine.test
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Real OkHttp threads against a MockWebServer WebSocket, so this runs under
 * runBlocking (whose event loop is woken by other threads) rather than
 * runTest's virtual-time scheduler.
 */
class SeatInventorySocketTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val socket = SeatInventorySocket(OkHttpClient(), json)

    private lateinit var serverSide: WebSocket
    private val connected = CountDownLatch(1)

    @Before
    fun start() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        serverSide = webSocket
                        connected.countDown()
                    }

                    // A real server answers a client close; MockWebServer leaves that to the test.
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun wsBaseUrl(): String = server.url("/").toString().trimEnd('/').replaceFirst("http://", "ws://")

    @Test
    fun `parses events, drops junk and other trips, completes on server close`() = runBlocking {
        socket.observe("T1", wsBaseUrl()).test(timeout = 10.seconds) {
            connected.await(5, TimeUnit.SECONDS)
            assertEquals("/v1/trips/T1/seat-events", server.takeRequest().path)
            assertEquals(1, socket.openCount)

            serverSide.send("this is not json")
            serverSide.send("""{"tripId":"OTHER","seatId":"1A","status":"LOCKED"}""")
            serverSide.send("""{"tripId":"T1","seatId":"1A","status":"LOCKED","lockedByUserId":"u9","lockExpiresAtEpochMillis":5}""")

            val event = awaitItem()
            assertEquals("1A", event.seatId)
            assertEquals("u9", event.lockedByUserId)
            assertEquals(5L, event.lockExpiresAtEpochMillis)

            serverSide.close(1000, "done")
            awaitComplete()
        }
        assertEquals(0, socket.openCount)
    }

    @Test
    fun `disconnect closes every socket for the trip`() = runBlocking {
        socket.observe("T1", wsBaseUrl()).test(timeout = 10.seconds) {
            connected.await(5, TimeUnit.SECONDS)
            socket.disconnect("T1")
            awaitComplete()
        }
        assertEquals(0, socket.openCount)
    }
}
