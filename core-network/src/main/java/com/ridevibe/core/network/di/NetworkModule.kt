package com.ridevibe.core.network.di

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
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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

    @Provides
    @Singleton
    fun provideOkHttpClient(
        deviceIdInterceptor: DeviceIdInterceptor,
        staffTokenInterceptor: StaffTokenInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // no timeout — WebSocket stays open
        .pingInterval(20, TimeUnit.SECONDS)
        // Identifies the caller to the CRS. Without it, hold/book/my-bookings
        // return 401. Added before logging so it is visible when logging is on.
        .addInterceptor(deviceIdInterceptor)
        // Staff console: adds x-admin-token / x-partner-token on /auth, /admin
        // and /partner only (never /v1), and drops an expired/revoked session
        // on 401. Also before logging so the header shows up in debug traces.
        .addInterceptor(staffTokenInterceptor)
        .apply {
            // Never log HTTP traffic in release builds — requests can carry
            // passenger names, contact numbers, and booking references.
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
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
