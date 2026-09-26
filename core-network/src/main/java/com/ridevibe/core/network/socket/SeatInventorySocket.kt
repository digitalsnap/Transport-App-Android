package com.ridevibe.core.network.socket

import com.ridevibe.core.network.dto.SeatStatusEventDto
import com.ridevibe.core.network.log.RideVibeLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * One OkHttp WebSocket per observer of a trip's seat inventory channel. The
 * server pushes `seat_status_changed` events any time a seat is locked,
 * released, or purchased by any passenger.
 *
 * Sockets are tracked by a per-subscription id, not by trip id: the seat map
 * and (for a round trip) a second observer of the same trip must never close
 * or overwrite each other's connection. [disconnect] closes every socket
 * open for a trip.
 */
@Singleton
class SeatInventorySocket @Inject constructor(
    @Named("webSocketClient") private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private class Subscription(val tripId: String, val socket: WebSocket)

    private val openSockets = ConcurrentHashMap<String, Subscription>()

    /** How many sockets are open right now (for diagnostics and tests). */
    val openCount: Int get() = openSockets.size

    fun observe(tripId: String, baseWsUrl: String): Flow<SeatStatusEventDto> = callbackFlow {
        val subscriptionId = UUID.randomUUID().toString()
        val request = Request.Builder()
            .url("$baseWsUrl/v1/trips/$tripId/seat-events")
            .build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = runCatching { json.decodeFromString(SeatStatusEventDto.serializer(), text) }
                    .onFailure { RideVibeLog.d("Dropped unparseable seat event on $tripId: ${it.message}") }
                    .getOrNull() ?: return
                if (event.tripId != tripId) {
                    RideVibeLog.d("Dropped seat event for ${event.tripId} on $tripId's channel")
                    return
                }
                trySend(event).onFailure { RideVibeLog.d("Dropped seat event on $tripId: observer gone") }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                // The ViewModel reconnects with backoff and resyncs the seat map.
                close(t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // OkHttp only reports onClosed once BOTH sides have sent a close
                // frame; answering the server's close here is what lets the
                // flow complete instead of hanging until the ping timeout.
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close() // closed cleanly on both sides: the flow completes, no error to show
            }
        }

        val socket = okHttpClient.newWebSocket(request, listener)
        openSockets[subscriptionId] = Subscription(tripId, socket)

        awaitClose {
            openSockets.remove(subscriptionId)
            socket.close(NORMAL_CLOSURE_CODE, "ViewModel scope cancelled")
        }
    }

    /** Closes every socket open for [tripId]; their flows complete normally. */
    fun disconnect(tripId: String) {
        openSockets.entries
            .filter { it.value.tripId == tripId }
            .forEach { (id, subscription) ->
                openSockets.remove(id)
                subscription.socket.close(NORMAL_CLOSURE_CODE, "Client disconnect")
            }
    }

    private companion object {
        const val NORMAL_CLOSURE_CODE = 1000
    }
}
