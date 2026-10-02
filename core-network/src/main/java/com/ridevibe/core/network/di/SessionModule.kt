package com.ridevibe.core.network.di

import android.content.Context
import com.ridevibe.core.domain.session.BookingCartStore
import com.ridevibe.core.network.session.PrefsBookingCartStore
import com.ridevibe.core.network.storage.CartStore
import com.ridevibe.core.network.storage.EncryptedPrefsKeyValueStore
import com.ridevibe.core.network.storage.IdentityStore
import com.ridevibe.core.network.storage.KeyValueStore
import com.ridevibe.core.network.storage.PassengerSessionPrefs
import com.ridevibe.core.network.storage.SharedPreferencesKeyValueStore
import com.ridevibe.core.network.storage.StaffSessionPrefs
import com.ridevibe.core.network.storage.TicketCacheStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * On-device session state: the passenger identity, the staff session, the
 * booking cart and the offline ticket cache — one preferences file each, so
 * clearing one (e.g. staff sign-out) never touches another.
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    /**
     * Encrypted: X-Device-Id is a bearer credential on the passenger API. The
     * file name changed with the move to encryption, so a device that had the
     * plain file gets a fresh id (its mock-era bookings are not worth keeping).
     */
    @Provides
    @Singleton
    @IdentityStore
    fun provideIdentityStore(@ApplicationContext context: Context): KeyValueStore =
        EncryptedPrefsKeyValueStore(context, "ridevibe_identity_v2")

    /** Encrypted: holds the Google/Facebook tokens of the signed-in rider. */
    @Provides
    @Singleton
    @PassengerSessionPrefs
    fun providePassengerSessionStore(@ApplicationContext context: Context): KeyValueStore =
        EncryptedPrefsKeyValueStore(context, "ridevibe_passenger_session_v2")

    /** Encrypted at rest: it holds a bearer credential for the staff console. */
    @Provides
    @Singleton
    @StaffSessionPrefs
    fun provideStaffSessionStore(@ApplicationContext context: Context): KeyValueStore =
        EncryptedPrefsKeyValueStore(context, "ridevibe_staff_session")

    @Provides
    @Singleton
    @CartStore
    fun provideCartStore(@ApplicationContext context: Context): KeyValueStore =
        SharedPreferencesKeyValueStore(context.getSharedPreferences("ridevibe_cart", Context.MODE_PRIVATE))

    /** Encrypted: cached tickets carry passenger names and scannable QR payloads. */
    @Provides
    @Singleton
    @TicketCacheStore
    fun provideTicketCacheStore(@ApplicationContext context: Context): KeyValueStore =
        EncryptedPrefsKeyValueStore(context, "ridevibe_ticket_cache_v2")

    @Provides
    @Singleton
    fun provideBookingCartStore(impl: PrefsBookingCartStore): BookingCartStore = impl
}
