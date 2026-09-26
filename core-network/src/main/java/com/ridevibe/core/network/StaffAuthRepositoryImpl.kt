package com.ridevibe.core.network

import com.ridevibe.core.domain.model.EmailCodeRequested
import com.ridevibe.core.domain.model.StaffAuthOptions
import com.ridevibe.core.domain.model.StaffSession
import com.ridevibe.core.domain.repository.StaffAuthRepository
import com.ridevibe.core.network.api.CrsApiException
import com.ridevibe.core.network.api.StaffApiService
import com.ridevibe.core.network.api.apiResult
import com.ridevibe.core.network.auth.StaffSessionStore
import com.ridevibe.core.network.dto.EmailCodeRequestDto
import com.ridevibe.core.network.dto.EmailCodeVerifyRequestDto
import com.ridevibe.core.network.dto.GoogleSignInRequestDto
import com.ridevibe.core.network.dto.LoginRequestDto
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `/auth/…` against the CRS. Every successful sign-in lands in
 * [StaffSessionStore], which is what the token interceptor and the other staff
 * repositories read — callers never see or pass the token themselves.
 */
@Singleton
class StaffAuthRepositoryImpl @Inject constructor(
    private val api: StaffApiService,
    private val sessionStore: StaffSessionStore,
) : StaffAuthRepository {

    override val session: StateFlow<StaffSession?> get() = sessionStore.session

    override suspend fun getAuthOptions(): Result<StaffAuthOptions> =
        apiResult { api.getAuthConfig().toDomain() }

    override suspend fun signInWithPassword(email: String, password: String): Result<StaffSession> =
        apiResult {
            api.login(LoginRequestDto(email = email.trim(), password = password)).toDomain().also(sessionStore::save)
        }

    override suspend fun requestEmailCode(email: String): Result<EmailCodeRequested> =
        apiResult { api.requestEmailCode(EmailCodeRequestDto(email = email.trim())).toDomain() }

    override suspend fun signInWithEmailCode(email: String, code: String): Result<StaffSession> =
        apiResult {
            api.verifyEmailCode(EmailCodeVerifyRequestDto(email = email.trim(), code = code.trim()))
                .toDomain()
                .also(sessionStore::save)
        }

    override suspend fun signInWithGoogle(idToken: String): Result<StaffSession> =
        apiResult {
            api.signInWithGoogle(GoogleSignInRequestDto(credential = idToken)).toDomain().also(sessionStore::save)
        }

    /**
     * Re-validates the persisted token with `/auth/me`. A session whose
     * server-side lifetime has already passed is dropped without a round
     * trip (the server would 401 anyway, and offline it would otherwise
     * linger forever). A 401 means the server no longer knows the session
     * (expired or revoked): the store is cleared and the call SUCCEEDS with
     * null so the UI simply shows sign-in. Any other failure (offline, 5xx)
     * keeps the persisted session and fails the Result.
     */
    override suspend fun refreshSession(): Result<StaffSession?> {
        val current = sessionStore.session.value ?: return Result.success(null)
        if (sessionStore.isExpired()) {
            sessionStore.clear()
            return Result.success(null)
        }
        return apiResult { api.me() }.fold(
            onSuccess = { me ->
                val refreshed = me.toDomain(token = current.token, expiresAtEpochMillis = current.expiresAtEpochMillis)
                sessionStore.save(refreshed)
                Result.success(refreshed)
            },
            onFailure = { error ->
                if (error is CrsApiException && error.code == 401) {
                    sessionStore.clear()
                    Result.success(null)
                } else {
                    Result.failure(error)
                }
            },
        )
    }

    /** Best-effort server-side revoke, then always forget the session locally. */
    override suspend fun signOut() {
        if (sessionStore.session.value != null) {
            apiResult { api.logout() } // failure is fine: token expires in ≤7 days anyway
        }
        sessionStore.clear()
    }
}
