package com.ridevibe.core.network.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ridevibe.core.network.BuildConfig
import com.ridevibe.core.network.api.CrsApiService
import com.ridevibe.core.network.api.StaffApiService
import com.ridevibe.core.network.auth.DeviceIdInterceptor
import com.ridevibe.core.network.auth.StaffTokenInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

// Base URLs come from BuildConfig (see core-network/build.gradle.kts) so a local
// or staging CRS is a -P flag, not a code edit.

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("wsBaseUrl")
    fun provideWsBaseUrl(): String = BuildConfig.WS_BASE_URL

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * The REST client. Timeouts are tuned for PH mobile networks: a slow 3G
     * search may legitimately take 20 s, but a call that has not finished in
     * 45 s is dead and the rider should see "no connection" rather than a
     * spinner. The WebSocket client is derived from this one below.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        deviceIdInterceptor: DeviceIdInterceptor,
        staffTokenInterceptor: StaffTokenInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        // Identifies the caller to the CRS on /v1 only. Without it, hold/book/
        // my-bookings return 401. Added before logging so it is visible when
        // logging is on.
        .addInterceptor(deviceIdInterceptor)
        // Staff console: adds x-admin-token / x-partner-token on /auth, /admin
        // and /partner only (never /v1), and drops an expired/revoked session
        // on 401. Also before logging so the header shows up in debug traces.
        .addInterceptor(staffTokenInterceptor)
        .apply {
            // Never log HTTP traffic in release builds — requests can carry
            // passenger names, contact numbers, and booking references. Even in
            // debug the credentials are redacted so a shared logcat is safe.
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("x-admin-token")
                        redactHeader("x-partner-token")
                        redactHeader("X-Device-Id")
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    /**
     * Same interceptors and connect timeout as REST, but no read or call
     * timeout: a seat-events socket idles for minutes between frames and
     * OkHttp's call timeout would otherwise tear the stream down. Liveness
     * comes from the inherited 20 s ping interval.
     */
    @Provides
    @Singleton
    @Named("webSocketClient")
    fun provideWebSocketClient(restClient: OkHttpClient): OkHttpClient = restClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideCrsApiService(retrofit: Retrofit): CrsApiService = retrofit.create(CrsApiService::class.java)

    /** Same Retrofit/OkHttp stack as the passenger API — same base URL, same interceptors. */
    @Provides
    @Singleton
    fun provideStaffApiService(retrofit: Retrofit): StaffApiService = retrofit.create(StaffApiService::class.java)
}
