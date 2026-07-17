package com.ridevibe.core.network.socket

import com.ridevibe.core.network.dto.SeatStatusEventDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around a single OkHttp WebSocket connection dedicated to one
 * trip's seat inventory channel. The server pushes `seat_status_changed`
 * events any time a seat is locked, released, or purchased by any passenger.
 */
@Singleton
class SeatInventorySocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val openSockets = mutableMapOf<String, WebSocket>()

    fun observe(tripId: String, baseWsUrl: String): Flow<SeatStatusEventDto> = callbackFlow {
        val request = Request.Builder()
            .url("$baseWsUrl/v1/trips/$tripId/seat-events")
            .build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { json.decodeFromString(SeatStatusEventDto.serializer(), text) }
                    .onSuccess { trySend(it) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        }

        val socket = okHttpClient.newWebSocket(request, listener)
        openSockets[tripId] = socket

        awaitClose {
            socket.close(NORMAL_CLOSURE_CODE, "ViewModel scope cancelled")
            openSockets.remove(tripId)
        }
    }

    fun disconnect(tripId: String) {
        openSockets.remove(tripId)?.close(NORMAL_CLOSURE_CODE, "Client disconnect")
    }

    private companion object {
        const val NORMAL_CLOSURE_CODE = 1000
    }
}
