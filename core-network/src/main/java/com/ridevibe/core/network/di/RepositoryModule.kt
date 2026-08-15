package com.ridevibe.core.network.di

import com.ridevibe.core.domain.repository.CheckoutRepository
import com.ridevibe.core.domain.repository.ItineraryRepository
import com.ridevibe.core.domain.repository.ProfileRepository
import com.ridevibe.core.domain.repository.SeatRepository
import com.ridevibe.core.domain.repository.SupportRepository
import com.ridevibe.core.domain.repository.TripRepository
import com.ridevibe.core.domain.repository.WalletRepository
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
 * MOCK-DATA SWITCH — flip to false (and delete the core-network/mock package)
 * when the real CRS backend goes live. Everything below routes through this
 * one constant, so going live is a one-line change plus a folder delete.
 */
private const val USE_MOCK_DATA = true

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
