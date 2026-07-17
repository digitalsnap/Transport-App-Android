package com.ridevibe.core.network.di

import com.ridevibe.core.domain.repository.CheckoutRepository
import com.ridevibe.core.domain.repository.SeatRepository
import com.ridevibe.core.network.CheckoutRepositoryImpl
import com.ridevibe.core.network.SeatRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSeatRepository(impl: SeatRepositoryImpl): SeatRepository

    @Binds
    @Singleton
    abstract fun bindCheckoutRepository(impl: CheckoutRepositoryImpl): CheckoutRepository

    // TripRepository impl follows the same @Binds pattern once feature-search's
    // SearchScreen/ViewModel lands and actually requests it.
}
