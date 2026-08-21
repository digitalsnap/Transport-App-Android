package com.ridevibe.core.network.di

import com.ridevibe.core.domain.repository.CheckoutRepository
import com.ridevibe.core.domain.repository.ItineraryRepository
import com.ridevibe.core.domain.repository.ProfileRepository
import com.ridevibe.core.domain.repository.SeatRepository
import com.ridevibe.core.domain.repository.SupportRepository
import com.ridevibe.core.domain.repository.TripRepository
import com.ridevibe.core.domain.repository.WalletRepository
import com.ridevibe.core.network.BuildConfig
import com.ridevibe.core.network.CheckoutRepositoryImpl
import com.ridevibe.core.network.SeatRepositoryImpl
import com.ridevibe.core.network.TripRepositoryImpl
import com.ridevibe.core.network.mock.MockCheckoutRepository
import com.ridevibe.core.network.mock.MockItineraryRepository
import com.ridevibe.core.network.mock.MockProfileRepository
import com.ridevibe.core.network.mock.MockSeatRepository
import com.ridevibe.core.network.mock.MockSupportRepository
import com.ridevibe.core.network.mock.MockWalletRepository
import com.ridevibe.core.network.mock.MockTripRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * MOCK-DATA SWITCH — build config now, not a source constant, so switching a
 * build between mock and real data never needs a code edit:
 *
 *     ./gradlew installDebug -Pridevibe.useMocks=false
 *
 * Defaults to true because the CRS backend does not exist yet (roadmap Phase 0).
 * Flip the default in gradle.properties once it does.
 *
 * KEEP the mock package after the backend lands — it is what lets the app build,
 * demo, and run UI tests with no server reachable. It is a dev asset, not debt.
 */
private val USE_MOCK_DATA = BuildConfig.USE_MOCK_DATA

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSeatRepository(
        real: Provider<SeatRepositoryImpl>,
        mock: Provider<MockSeatRepository>,
    ): SeatRepository = if (USE_MOCK_DATA) mock.get() else real.get()

    @Provides
    @Singleton
    fun provideTripRepository(
        real: Provider<TripRepositoryImpl>,
        mock: Provider<MockTripRepository>,
    ): TripRepository = if (USE_MOCK_DATA) mock.get() else real.get()

    @Provides
    @Singleton
    fun provideCheckoutRepository(
        real: Provider<CheckoutRepositoryImpl>,
        mock: Provider<MockCheckoutRepository>,
    ): CheckoutRepository = if (USE_MOCK_DATA) mock.get() else real.get()

    @Provides
    @Singleton
    fun provideProfileRepository(
        mock: Provider<MockProfileRepository>,
    ): ProfileRepository = mock.get() // TODO: swap in the real impl once the CRS has profile endpoints

    @Provides
    @Singleton
    fun provideWalletRepository(
        mock: Provider<MockWalletRepository>,
    ): WalletRepository = mock.get() // TODO: real impl arrives with the payments integration

    @Provides
    @Singleton
    fun provideSupportRepository(
        mock: Provider<MockSupportRepository>,
    ): SupportRepository = mock.get() // TODO: real impl arrives with the support-chat backend

    @Provides
    @Singleton
    fun provideItineraryRepository(
        mock: Provider<MockItineraryRepository>,
    ): ItineraryRepository = mock.get() // TODO: sync itineraries to the account once the CRS supports them
}
