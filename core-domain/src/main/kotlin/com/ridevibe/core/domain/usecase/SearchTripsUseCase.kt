package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.TripRepository
import javax.inject.Inject

class SearchTripsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    suspend operator fun invoke(
        origin: String,
        destination: String,
        departureDateEpochMillis: Long,
        busClass: BusClass? = null,
    ): Result<List<Trip>> = tripRepository.searchTrips(origin, destination, departureDateEpochMillis, busClass)
}
