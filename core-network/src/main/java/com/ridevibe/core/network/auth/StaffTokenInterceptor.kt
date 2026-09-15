package com.ridevibe.core.network.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the staff session token to `/auth`, `/admin` and `/partner`
 * requests: `x-admin-token` for an ADMIN session, `x-partner-token` for a
 * PARTNER one (an ADMIN session reads `/partner/api/…` through `x-admin-token`
 * plus `?operatorId=`, exactly as the server's partner middleware expects).
 *
 * `/v1/…` — the passenger contract — is never touched; it stays on
 * X-Device-Id alone.
 *
 * A 401 from `/admin` or `/partner` while a token WAS attached means the
 * session expired (7 days) or was revoked, so the store is cleared and the UI
 * falls back to the sign-in screen. `/auth/me` is left to
 * [com.ridevibe.core.network.StaffAuthRepositoryImpl.refreshSession].
 */
@Singleton
class StaffTokenInterceptor @Inject constructor(
    private val sessionStore: StaffSessionStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (!path.isStaffPath()) return chain.proceed(request)

        val session = sessionStore.session.value ?: return chain.proceed(request)
        val header = if (session.isAdmin) HEADER_ADMIN_TOKEN else HEADER_PARTNER_TOKEN
        val response = chain.proceed(
            request.newBuilder().header(header, session.token).build(),
        )

        if (response.code == 401 && (path.startsWith(PATH_ADMIN) || path.startsWith(PATH_PARTNER))) {
            // Only drop the session that was actually rejected — a sign-in that
            // raced this response must not be wiped by a stale 401.
            if (sessionStore.session.value?.token == session.token) sessionStore.clear()
        }
        return response
    }

    private fun String.isStaffPath(): Boolean =
        startsWith(PATH_AUTH) || startsWith(PATH_ADMIN) || startsWith(PATH_PARTNER)

    private companion object {
        const val HEADER_ADMIN_TOKEN = "x-admin-token"
        const val HEADER_PARTNER_TOKEN = "x-partner-token"
        const val PATH_AUTH = "/auth"
        const val PATH_ADMIN = "/admin"
        const val PATH_PARTNER = "/partner"
    }
}
