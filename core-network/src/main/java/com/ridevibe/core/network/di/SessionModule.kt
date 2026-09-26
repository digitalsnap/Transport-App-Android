package com.ridevibe.core.network.di

import android.content.Context
import com.ridevibe.core.domain.session.BookingCartStore
import com.ridevibe.core.network.session.PrefsBookingCartStore
import com.ridevibe.core.network.storage.CartStore
import com.ridevibe.core.network.storage.EncryptedPrefsKeyValueStore
import com.ridevibe.core.network.storage.IdentityStore
import com.ridevibe.core.network.storage.KeyValueStore
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

    @Provides
    @Singleton
    @IdentityStore
    fun provideIdentityStore(@ApplicationContext context: Context): KeyValueStore =
        SharedPreferencesKeyValueStore(context.getSharedPreferences("ridevibe_identity", Context.MODE_PRIVATE))

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

    @Provides
    @Singleton
    @TicketCacheStore
    fun provideTicketCacheStore(@ApplicationContext context: Context): KeyValueStore =
        SharedPreferencesKeyValueStore(context.getSharedPreferences("ridevibe_ticket_cache", Context.MODE_PRIVATE))

    @Provides
    @Singleton
    fun provideBookingCartStore(impl: PrefsBookingCartStore): BookingCartStore = impl
}
