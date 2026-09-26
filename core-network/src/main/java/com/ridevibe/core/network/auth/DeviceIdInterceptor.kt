package com.ridevibe.core.network.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches `X-Device-Id` to passenger (`/v1/…`) requests only. The CRS returns
 * 401 on hold, booking and my-bookings routes without it; the staff surfaces
 * (`/auth`, `/admin`, `/partner`) authenticate with a session token and must
 * never see the passenger identity (see CLAUDE.md section 5).
 *
 * Installed on the shared OkHttpClient, so the WebSocket handshake for
 * `/v1/trips/{tripId}/seat-events` carries it too — the socket route does not
 * require it today, but the server can start attributing events without a
 * client change.
 *
 * On the way back it captures the `X-User-Id` header the server adds to every
 * authenticated response, which is the id the app compares against
 * `lockedByUserId` to recognise its own holds (see [CurrentUserId]).
 */
@Singleton
class DeviceIdInterceptor @Inject constructor(
    private val deviceIdProvider: DeviceIdProvider,
    private val currentUserId: CurrentUserId,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.startsWith(PASSENGER_PATH_PREFIX)) return chain.proceed(request)

        val response = chain.proceed(
            request.newBuilder()
                .header(HEADER_DEVICE_ID, deviceIdProvider.deviceId)
                .build(),
        )
        currentUserId.update(response.header(HEADER_USER_ID))
        return response
    }

    private companion object {
        const val PASSENGER_PATH_PREFIX = "/v1/"
        const val HEADER_DEVICE_ID = "X-Device-Id"
        const val HEADER_USER_ID = "X-User-Id"
    }
}
