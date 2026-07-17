package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.TripRepository
import javax.inject.Inject

class GetTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    suspend operator fun invoke(tripId: String): Result<Trip> = tripRepository.getTrip(tripId)
}
